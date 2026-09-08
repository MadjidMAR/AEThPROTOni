package io.github.immaghzbad.aetherst.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.immaghzbad.aetherst.MainActivity
import io.github.immaghzbad.aetherst.R
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.shared.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.shared.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AetherTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = ConnectionController.status
            .onEach { updateTile(it) }
            .launchIn(scope)
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        val repo = AetherConfigRepository.getInstance(getSettings(PlatformContext(this)))
        if (!repo.isOnboardingComplete.value) {
            startApp()
            return
        }

        val config = repo.config.value
        val state = ConnectionController.status.value

        when (state) {
            ConnectionStatus.RUNNING -> {
                if (config.connectionMode == ConnectionMode.TUNNEL) {
                    AetherVpnService.stopVpn(this)
                } else {
                    AetherProxyService.stopProxy(this)
                }
            }
            ConnectionStatus.STOPPED, ConnectionStatus.ERROR -> {
                if (config.connectionMode == ConnectionMode.TUNNEL) {
                    AetherVpnService.startVpn(this)
                } else {
                    AetherProxyService.startProxy(this)
                }
            }
            else -> Unit
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(state: ConnectionStatus) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_aether)

        val config = AetherConfigRepository.getInstance(getSettings(PlatformContext(this))).config.value

        when (state) {
            ConnectionStatus.RUNNING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = when (config.connectionMode) {
                        ConnectionMode.TUNNEL -> "VPN Connected"
                        ConnectionMode.PROXY_ONLY -> {
                            if (config.httpProxyEnabled) {
                                "Proxy \u2022 SOCKS5 :${config.socksPort} \u2022 HTTP :${config.httpPort}"
                            } else {
                                "Proxy \u2022 SOCKS5 :${config.socksPort}"
                            }
                        }
                        ConnectionMode.SYSTEM_PROXY -> "Proxy \u2022 HTTP :${config.httpPort}"
                    }
                }
            }
            ConnectionStatus.STOPPED, ConnectionStatus.ERROR -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Disconnected"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Connecting..."
                }
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        scope.cancel()
        super.onDestroy()
    }
}
