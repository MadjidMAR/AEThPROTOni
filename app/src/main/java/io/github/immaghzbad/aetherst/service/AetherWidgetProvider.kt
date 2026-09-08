package io.github.immaghzbad.aetherst.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.graphics.toColorInt
import android.widget.RemoteViews
import io.github.immaghzbad.aetherst.R
import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.shared.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.shared.model.ConnectionMode
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class AetherWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "io.github.immaghzbad.aetherst.WIDGET_TOGGLE"
        const val ACTION_CHANGE_PROTOCOL = "io.github.immaghzbad.aetherst.WIDGET_CHANGE_PROTOCOL"
        private const val DEBOUNCE_MS = 800L
        private val lastClickAt = mutableMapOf<String, Long>()

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AetherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val status = ConnectionController.status.value
            val config = AetherConfigRepository.getInstance(getSettings(PlatformContext(context))).config.value

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, status, config)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            status: ConnectionStatus,
            config: io.github.immaghzbad.aetherst.shared.model.AetherConfig
        ) {
            val views = RemoteViews(context.packageName, R.layout.aether_widget)

            val statusText = when (status) {
                ConnectionStatus.RUNNING, ConnectionStatus.TUN_ACTIVE -> when (config.connectionMode) {
                    ConnectionMode.TUNNEL -> "VPN Connected"
                    ConnectionMode.PROXY_ONLY -> {
                        if (config.httpProxyEnabled) {
                            "Proxy Active \u2022 SOCKS5 :${config.socksPort} \u2022 HTTP :${config.httpPort}"
                        } else {
                            "Proxy Active \u2022 SOCKS5 :${config.socksPort}"
                        }
                    }
                    ConnectionMode.SYSTEM_PROXY -> "Proxy Active \u2022 HTTP :${config.httpPort}"
                }
                ConnectionStatus.STARTING, ConnectionStatus.VALIDATING, ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.SOCKS_READY, ConnectionStatus.RECONNECTING -> "Connecting..."
                ConnectionStatus.STOPPING -> "Disconnecting..."
                ConnectionStatus.ERROR, ConnectionStatus.FAILED -> "Error"
                else -> "Disconnected"
            }

            val statusColor = when (status) {
                ConnectionStatus.RUNNING, ConnectionStatus.TUN_ACTIVE -> "#34C759".toColorInt()
                ConnectionStatus.STARTING, ConnectionStatus.VALIDATING, ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.SOCKS_READY, ConnectionStatus.RECONNECTING -> "#FF9500".toColorInt()
                ConnectionStatus.ERROR, ConnectionStatus.FAILED -> "#FF3B30".toColorInt()
                else -> "#8E8E93".toColorInt()
            }

            val buttonRes = when (status) {
                ConnectionStatus.RUNNING, ConnectionStatus.TUN_ACTIVE -> R.drawable.widget_button_green
                ConnectionStatus.STARTING, ConnectionStatus.VALIDATING, ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.SOCKS_READY, ConnectionStatus.RECONNECTING -> R.drawable.widget_button_orange
                else -> R.drawable.widget_button_blue
            }

            views.setTextViewText(R.id.widget_status, statusText)
            views.setTextColor(R.id.widget_status, statusColor)
            views.setImageViewResource(R.id.widget_button, android.R.drawable.ic_lock_power_off)
            views.setInt(R.id.widget_button, "setBackgroundResource", buttonRes)

            setupProtocolButton(context, views, R.id.proto_masque, AetherProtocol.MASQUE, config.protocol, appWidgetId)
            setupProtocolButton(context, views, R.id.proto_wire, AetherProtocol.WG, config.protocol, appWidgetId)
            setupProtocolButton(context, views, R.id.proto_gool, AetherProtocol.GOOL, config.protocol, appWidgetId)

            val toggleIntent = Intent(context, AetherWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
                data = "aether://widget/$appWidgetId/toggle".toUri()
            }
            val togglePending = PendingIntent.getBroadcast(
                context, (appWidgetId shl 16) or 0xFF00, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_button_container, togglePending)

            val launchIntent = Intent(context, io.github.immaghzbad.aetherst.MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPending = PendingIntent.getActivity(
                context, (appWidgetId shl 16) or 0xFF01, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launchPending)
            views.setOnClickPendingIntent(R.id.widget_title, launchPending)
            views.setOnClickPendingIntent(R.id.widget_status, launchPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setupProtocolButton(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            protocol: AetherProtocol,
            currentProtocol: AetherProtocol,
            appWidgetId: Int
        ) {
            val isActive = protocol == currentProtocol
            val bgRes = if (isActive) R.drawable.widget_protocol_active_bg else R.drawable.widget_protocol_inactive_bg
            val textColor = if (isActive) "#007AFF".toColorInt() else "#8E8E93".toColorInt()
            
            views.setInt(viewId, "setBackgroundResource", bgRes)
            views.setTextColor(viewId, textColor)

            if (isActive) {
                views.setOnClickPendingIntent(viewId, null)
                return
            }
            val intent = Intent(context, AetherWidgetProvider::class.java).apply {
                action = ACTION_CHANGE_PROTOCOL
                putExtra("protocol", protocol.name)
                data = "aether://widget/$appWidgetId/${protocol.name}".toUri()
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, (appWidgetId shl 16) or protocol.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val status = ConnectionController.status.value
        val config = AetherConfigRepository.getInstance(getSettings(PlatformContext(context))).config.value
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, status, config)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        synchronized(lastClickAt) {
            appWidgetIds.forEach { id ->
                lastClickAt.keys.removeIf { it.contains("aether://widget/$id/") }
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        synchronized(lastClickAt) { lastClickAt.clear() }
        super.onDisabled(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_TOGGLE && action != ACTION_CHANGE_PROTOCOL && action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            super.onReceive(context, intent)
            return
        }
        if (action == ACTION_TOGGLE || action == ACTION_CHANGE_PROTOCOL) {
            if (action !in setOf(ACTION_TOGGLE, ACTION_CHANGE_PROTOCOL)) {
                super.onReceive(context, intent)
                return
            }
            val key = "${intent.action}:${intent.getStringExtra("protocol") ?: "toggle"}:${intent.dataString ?: ""}"
            val now = android.os.SystemClock.elapsedRealtime()
            synchronized(lastClickAt) {
                val last = lastClickAt[key] ?: 0L
                if (now - last < DEBOUNCE_MS) return
                lastClickAt[key] = now
            }
        }
        super.onReceive(context, intent)
        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AetherConfigRepository.getInstance(getSettings(PlatformContext(context)))
                when (action) {
                    ACTION_TOGGLE -> {
                        if (!repository.isOnboardingComplete.value) {
                            launchMainActivity(context)
                            return@launch
                        }
                        val config = repository.config.value
                        if (config.connectionMode == ConnectionMode.TUNNEL) {
                            val needsPrep = withContext(Dispatchers.IO) { android.net.VpnService.prepare(context) != null }
                            if (needsPrep) {
                                launchMainActivity(context)
                                return@launch
                            }
                        }
                        val status = ConnectionController.status.value
                        if (status == ConnectionStatus.STARTING || status == ConnectionStatus.VALIDATING || status == ConnectionStatus.DATAPLANE_VALIDATED || status == ConnectionStatus.SOCKS_READY || status == ConnectionStatus.STOPPING) return@launch
                        if (status == ConnectionStatus.RUNNING || status == ConnectionStatus.RECONNECTING) {
                            if (config.connectionMode == ConnectionMode.TUNNEL) AetherVpnService.stopVpn(context) else AetherProxyService.stopProxy(context)
                        } else if (status == ConnectionStatus.STOPPED || status == ConnectionStatus.ERROR || status == ConnectionStatus.FAILED) {
                            if (config.connectionMode == ConnectionMode.TUNNEL) AetherVpnService.startVpn(context) else AetherProxyService.startProxy(context)
                        }
                    }
                    ACTION_CHANGE_PROTOCOL -> {
                        val protocolName = intent.getStringExtra("protocol") ?: return@launch
                        val nextProtocol = runCatching { AetherProtocol.valueOf(protocolName) }.getOrNull() ?: return@launch
                        val currentConfig = repository.config.value
                        if (currentConfig.protocol == nextProtocol) return@launch
                        val status = ConnectionController.status.value
                        if (status == ConnectionStatus.STARTING || status == ConnectionStatus.VALIDATING || status == ConnectionStatus.DATAPLANE_VALIDATED || status == ConnectionStatus.SOCKS_READY || status == ConnectionStatus.STOPPING) return@launch
                        repository.updateConfig(currentConfig.copy(protocol = nextProtocol))
                        if (status == ConnectionStatus.RUNNING || status == ConnectionStatus.RECONNECTING) {
                            if (currentConfig.connectionMode == ConnectionMode.TUNNEL) AetherVpnService.restartVpn(context) else AetherProxyService.restartProxy(context)
                        }
                        updateAllWidgets(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun launchMainActivity(context: Context) {
        try {
            val launchIntent = Intent(context, io.github.immaghzbad.aetherst.MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            LogRepository.w("Widget launch failed: ${e.message}")
            try {
                val fallback = Intent(context, io.github.immaghzbad.aetherst.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                LogRepository.w("Widget fallback failed: ${e2.message}")
            }
        }
    }
}
