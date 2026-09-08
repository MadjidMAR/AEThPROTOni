package io.github.immaghzbad.aetherst.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.platform.isDesktop
import io.github.immaghzbad.aetherst.platform.isWindows
import io.github.immaghzbad.aetherst.shared.data.IpInfo
import io.github.immaghzbad.aetherst.shared.data.PingState
import io.github.immaghzbad.aetherst.shared.data.PsiphonEgressRegistry
import io.github.immaghzbad.aetherst.shared.model.AetherConfig
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.shared.model.ConnectionMode
import io.github.immaghzbad.aetherst.shared.model.PsiphonChainMode
import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.model.SessionTraffic
import io.github.immaghzbad.aetherst.shared.ui.components.CountryFlag
import io.github.immaghzbad.aetherst.shared.ui.components.IosPickerRow
import io.github.immaghzbad.aetherst.shared.ui.components.WorldMapCanvas
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings
import io.github.immaghzbad.aetherst.shared.i18n.StringsFa
import io.github.immaghzbad.aetherst.shared.util.CountryNames
import kotlinx.coroutines.launch

// ─── Proton VPN Color Scheme ──────────────────────────────────────────────────
private val PvBackground = Color(0xFF18181B)
private val PvSurface = Color(0xFF27272A)
private val PvSurfaceLight = Color(0xFF3F3F46)
private val PvPurple = Color(0xFF6D4AFF)
private val PvGreen = Color(0xFF30D158)
private val PvRed = Color(0xFFFF6B6B)
private val PvAmber = Color(0xFFFF9F0A)
private val PvTextPrimary = Color(0xFFECECF1)
private val PvTextSecondary = Color(0xFFA1A1AA)
private val PvDivider = Color(0xFF3F3F46)

// ─── Server list with country codes ───────────────────────────────────────────
private data class ServerEntry(val name: String, val countryCode: String, val cityCount: Int)

private val serverEntries = listOf(
    ServerEntry("New York", "US", 3),
    ServerEntry("Miami", "US", 2),
    ServerEntry("Chicago", "US", 1),
    ServerEntry("Dallas", "US", 1),
    ServerEntry("Los Angeles", "US", 2),
    ServerEntry("Toronto", "CA", 2),
    ServerEntry("Vancouver", "CA", 1),
    ServerEntry("São Paulo", "BR", 2),
    ServerEntry("Buenos Aires", "AR", 1),
    ServerEntry("Bogotá", "CO", 1),
    ServerEntry("Lima", "PE", 1),
    ServerEntry("London", "GB", 3),
    ServerEntry("Paris", "FR", 2),
    ServerEntry("Frankfurt", "DE", 2),
    ServerEntry("Amsterdam", "NL", 2),
    ServerEntry("Warsaw", "PL", 1),
    ServerEntry("Stockholm", "SE", 1),
    ServerEntry("Madrid", "ES", 1),
    ServerEntry("Cape Town", "ZA", 1),
    ServerEntry("Nairobi", "KE", 1),
    ServerEntry("Lagos", "NG", 1),
    ServerEntry("Cairo", "EG", 1),
    ServerEntry("Tokyo", "JP", 2),
    ServerEntry("Singapore", "SG", 2),
    ServerEntry("Hong Kong", "HK", 2),
    ServerEntry("Mumbai", "IN", 2),
    ServerEntry("Dubai", "AE", 1),
    ServerEntry("Seoul", "KR", 2),
    ServerEntry("Taipei", "TW", 1),
    ServerEntry("Jakarta", "ID", 1),
    ServerEntry("Sydney", "AU", 2),
    ServerEntry("Auckland", "NZ", 1),
)

// ─── Derived: grouped by country for the sheet ────────────────────────────────
private data class CountryServerGroup(val countryCode: String, val servers: List<ServerEntry>)

private val groupedServers: List<CountryServerGroup> by lazy {
    serverEntries.groupBy { it.countryCode }
        .map { (cc, servers) -> CountryServerGroup(cc, servers) }
        .sortedBy { CountryNames.display(it.countryCode) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DashboardScreen — Proton VPN style
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun DashboardScreen(
    config: AetherConfig,
    connectionStatus: ConnectionStatus,
    elapsedSeconds: Long,
    sessionTraffic: SessionTraffic,
    ipInfo: IpInfo = IpInfo(),
    pingState: PingState = PingState(),
    appVersion: String = "1.0.0",
    onToggleVpn: () -> Unit,
    onForceStop: () -> Unit = {},
    onUpdateConfig: (AetherConfig) -> Unit = {},
    onTogglePsiphon: (Boolean) -> Unit = {},
    onRefreshIpInfo: () -> Unit = {},
    onRefreshPing: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    platformContext: PlatformContext? = null,
    onSwipeDragging: (Boolean) -> Unit = {}
) {
    var showProxyOverlay by remember { mutableStateOf(true) }
    var showAdminRequiredDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var supportDialogAuto by remember { mutableStateOf(true) }
    var showPsiphonSheet by remember { mutableStateOf(false) }
    var showServerListSheet by remember { mutableStateOf(false) }
    val strings = LocalAppStrings.current
    val uriHandler = LocalUriHandler.current
    val settings = platformContext?.let { getSettings(it) }

    LaunchedEffect(Unit) {
        if (settings != null && !settings.getBoolean("support_dialog_dismissed", false)) {
            supportDialogAuto = true
            showSupportDialog = true
        }
    }
    val systemUtils = platformContext?.let { getSystemUtils(it) }

    LaunchedEffect(connectionStatus) {
        if (connectionStatus != ConnectionStatus.RUNNING) {
            showProxyOverlay = true
        }
    }

    // Determine selected server name from config
    val selectedServerName = remember(config, connectionStatus) {
        if (connectionStatus == ConnectionStatus.RUNNING || connectionStatus == ConnectionStatus.TUN_ACTIVE) {
            "London" // Default connected server
        } else null
    }

    val isConnected = connectionStatus == ConnectionStatus.RUNNING || connectionStatus == ConnectionStatus.TUN_ACTIVE
    val isWorking = connectionStatus in setOf(
        ConnectionStatus.STARTING, ConnectionStatus.VALIDATING,
        ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.SOCKS_READY,
        ConnectionStatus.RECONNECTING, ConnectionStatus.STOPPING
    )
    val isError = connectionStatus == ConnectionStatus.ERROR || connectionStatus == ConnectionStatus.FAILED

    // Status color
    val statusColor by animateColorAsState(
        targetValue = when {
            isConnected -> PvGreen
            isWorking -> PvPurple
            isError -> PvRed
            else -> PvTextSecondary
        },
        label = "statusColor"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PvBackground)
    ) {
        val screenWidth = this.maxWidth
        val screenHeight = this.maxHeight
        val baseScale = screenWidth.value / 411f
        val scaleFactor = if (isDesktop) (baseScale * 0.82f).coerceIn(0.65f, 0.90f) else baseScale.coerceIn(0.7f, 1.1f)
        val horizontalPadding = when {
            isDesktop -> 12.dp
            screenWidth < 360.dp -> 12.dp
            else -> 16.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomContentPadding + 10.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ═══ TOP SECTION ═══
            Column(
                modifier = Modifier.padding(top = if (isDesktop) 8.dp else 36.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── App title row with version badge ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.APP_TITLE,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PvTextPrimary,
                        fontSize = (20 * scaleFactor).sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PvSurface,
                            modifier = Modifier.clickable {
                                supportDialogAuto = false
                                showSupportDialog = true
                            }
                        ) {
                            Text(
                                text = "v$appVersion",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PvTextSecondary,
                                fontSize = (9 * scaleFactor).sp
                            )
                        }
                    }
                }

                // ── Status row: dot + status text + protocol badge ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Animated status dot
                        Box(
                            modifier = Modifier
                                .size((8 * scaleFactor).dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (connectionStatus) {
                                ConnectionStatus.RUNNING, ConnectionStatus.TUN_ACTIVE -> "Connected"
                                ConnectionStatus.STARTING -> "Starting"
                                ConnectionStatus.VALIDATING, ConnectionStatus.DATAPLANE_VALIDATED -> "Validating"
                                ConnectionStatus.SOCKS_READY -> "Connecting"
                                ConnectionStatus.RECONNECTING -> "Reconnecting"
                                ConnectionStatus.STOPPING -> "Stopping"
                                ConnectionStatus.ERROR, ConnectionStatus.FAILED -> "Error"
                                ConnectionStatus.STOPPED -> "Disconnected"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            fontSize = (13 * scaleFactor).sp
                        )
                    }

                    // Protocol badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PvSurface
                    ) {
                        val protocolText = config.protocol.displayName
                        Text(
                            text = protocolText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PvPurple,
                            fontSize = (9 * scaleFactor).sp
                        )
                    }
                }

            }

            // ═══ CENTER SECTION: Map + Power Button + Stats ═══
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ── World Map ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    WorldMapCanvas(
                        selectedServer = selectedServerName,
                        connectionStatus = connectionStatus,
                        onServerSelected = { },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Quick Connect Button ──
                QuickConnectButton(
                    connectionStatus = connectionStatus,
                    onToggle = {
                        if (connectionStatus == ConnectionStatus.STOPPING) {
                            onForceStop()
                        } else if (isWindows && config.connectionMode == ConnectionMode.TUNNEL && systemUtils?.isAdministrator() == false) {
                            showAdminRequiredDialog = true
                        } else {
                            onToggleVpn()
                        }
                    },
                    onRecover = onForceStop,
                    scaleFactor = scaleFactor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Connection Stats Row ──
                if (isConnected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PvSurface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timer
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatTime(elapsedSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PvTextPrimary,
                                fontSize = (14 * scaleFactor).sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Upload
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "▲ ${formatTrafficBytes(sessionTraffic.uploadedBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PvTextPrimary,
                                    fontSize = (10 * scaleFactor).sp
                                )
                                Text(
                                    text = formatSpeedValue(sessionTraffic.uploadSpeedBps),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PvTextSecondary,
                                    fontSize = (9 * scaleFactor).sp
                                )
                            }

                            // Download
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "▼ ${formatTrafficBytes(sessionTraffic.downloadedBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PvTextPrimary,
                                    fontSize = (10 * scaleFactor).sp
                                )
                                Text(
                                    text = formatSpeedValue(sessionTraffic.downloadSpeedBps),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PvTextSecondary,
                                    fontSize = (9 * scaleFactor).sp
                                )
                            }
                        }
                    }
                } else if (isError || connectionStatus == ConnectionStatus.RECONNECTING) {
                    val isReconnecting = connectionStatus == ConnectionStatus.RECONNECTING
                    val bg = if (isReconnecting) PvAmber.copy(alpha = 0.12f) else PvRed.copy(alpha = 0.1f)
                    val tint = if (isReconnecting) PvAmber else PvRed
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = bg)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isReconnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, null, tint = tint, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isReconnecting) {
                                    if (config.smartReconnect) "${strings.RECONNECTING_AUTO} (${config.reconnectRetryLimit} ${strings.LABEL_COUNTED} • ${config.reconnectSecs}s)" else strings.STATUS_RECONNECTING
                                } else {
                                    if (config.smartReconnect) strings.CONNECTION_FAILED_RETRY else strings.CONNECTION_FAILED_TRY
                                },
                                color = tint,
                                fontSize = (11 * scaleFactor).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ═══ BOTTOM SECTION ═══
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Server Selector Row ──
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showServerListSheet = true },
                    color = PvSurface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = PvPurple,
                                modifier = Modifier.size((18 * scaleFactor).dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Exit Location",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PvTextSecondary,
                                    fontSize = (9 * scaleFactor).sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = selectedServerName ?: "Auto (Recommended)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PvTextPrimary,
                                    fontSize = (13 * scaleFactor).sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = PvTextSecondary,
                            modifier = Modifier.size((20 * scaleFactor).dp)
                        )
                    }
                }

                // ── Error/reconnecting status ──

                // ── Psiphon Chain Card (minimal) ──
                run {
                    val psiphonAllowed = true
                    val psiphonOn = config.psiphonEnabled && psiphonAllowed
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = PvSurface
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PvPurple.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Shield, null, tint = PvPurple, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            strings.PSIPHON_CHAIN,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PvTextPrimary,
                                            fontSize = (12 * scaleFactor).sp
                                        )
                                        Text(
                                            if (config.psiphonEnabled) strings.PSIPHON_OVER_GOOL else strings.PSIPHON_ROUTE_VIA,
                                            color = PvTextSecondary,
                                            fontSize = (10 * scaleFactor).sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = config.psiphonEnabled && psiphonAllowed,
                                    onCheckedChange = { onTogglePsiphon(it) },
                                    enabled = psiphonAllowed && (connectionStatus == ConnectionStatus.STOPPED || connectionStatus == ConnectionStatus.ERROR),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PvPurple,
                                        checkedBorderColor = Color.Transparent,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = PvSurfaceLight,
                                        uncheckedBorderColor = Color.Transparent,
                                        disabledCheckedTrackColor = PvPurple.copy(alpha = 0.4f),
                                        disabledCheckedThumbColor = Color.White.copy(alpha = 0.9f),
                                        disabledCheckedBorderColor = Color.Transparent,
                                        disabledUncheckedTrackColor = PvSurfaceLight.copy(alpha = 0.6f),
                                        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                        disabledUncheckedBorderColor = Color.Transparent
                                    )
                                )
                            }
                            if (psiphonOn) {
                                HorizontalDivider(color = PvDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showPsiphonSheet = true }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PvPurple.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Settings, null, tint = PvPurple, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            strings.SHOW_MORE_PSIPHON,
                                            fontWeight = FontWeight.Medium,
                                            color = PvTextPrimary,
                                            fontSize = (11 * scaleFactor).sp
                                        )
                                        Text(
                                            strings.SHOW_MORE_SUBTITLE,
                                            color = PvTextSecondary,
                                            fontSize = (9 * scaleFactor).sp
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PvTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }


                if (isDesktop && isWindows) {
                    IosConnectionModeSegmentedControl(
                        selectedMode = config.connectionMode,
                        onModeSelected = { onUpdateConfig(config.copy(connectionMode = it)) },
                        enabled = connectionStatus == ConnectionStatus.STOPPED || connectionStatus == ConnectionStatus.ERROR,
                        scaleFactor = scaleFactor
                    )
                }
            }
        }

        // ═══ OVERLAYS ═══
        val offsetY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(showProxyOverlay) {
            if (showProxyOverlay) {
                offsetY.snapTo(0f)
            }
        }

        AnimatedVisibility(
            visible = (config.connectionMode == ConnectionMode.PROXY_ONLY || config.connectionMode == ConnectionMode.SYSTEM_PROXY) && connectionStatus == ConnectionStatus.RUNNING && showProxyOverlay && !isWindows,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp)
                .graphicsLayer { translationY = offsetY.value }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetY.value < -100f) {
                                    showProxyOverlay = false
                                } else {
                                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            scope.launch {
                                offsetY.snapTo((offsetY.value + dragAmount).coerceAtMost(20f))
                            }
                        }
                    )
                }
        ) {
            ProxyOverlayPill(
                host = config.socksHost,
                socksPort = config.socksPort,
                httpPort = config.httpPort,
                onHide = { showProxyOverlay = false },
                onCopy = onCopy,
                scaleFactor = scaleFactor,
                psiphonEnabled = config.psiphonEnabled,
                psiphonPort = config.psiphonSocksPort
            )
        }

        // ═══ DIALOGS ═══
        if (showAdminRequiredDialog) {
            AdminRequiredDialog(
                onRelaunch = {
                    showAdminRequiredDialog = false
                    systemUtils?.relaunchAsAdmin()
                },
                onDismiss = { showAdminRequiredDialog = false },
                scaleFactor = scaleFactor
            )
        }

        if (showSupportDialog) {
            SupportDialog(
                autoShow = supportDialogAuto,
                onJoin = {
                    settings?.putBoolean("support_dialog_dismissed", true)
                    showSupportDialog = false
                    uriHandler.openUri(TelegramChannelUrl)
                },
                onSkip = {
                    settings?.putBoolean("support_dialog_dismissed", true)
                    showSupportDialog = false
                },
                onCancel = { showSupportDialog = false },
                scaleFactor = scaleFactor
            )
        }

        if (showPsiphonSheet) {
            PsiphonOptionsSheet(
                config = config,
                onUpdateConfig = onUpdateConfig,
                onDismiss = { showPsiphonSheet = false },
                scaleFactor = scaleFactor
            )
        }

        if (showServerListSheet) {
            ServerListSheet(
                selectedServer = selectedServerName,
                onServerSelected = { },
                onDismiss = { showServerListSheet = false },
                scaleFactor = scaleFactor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// QuickConnectButton — large circular power button with glow
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickConnectButton(
    connectionStatus: ConnectionStatus,
    onToggle: () -> Unit,
    onRecover: () -> Unit = {},
    scaleFactor: Float = 1f
) {
    val isConnected = connectionStatus == ConnectionStatus.RUNNING || connectionStatus == ConnectionStatus.TUN_ACTIVE
    val isWorking = connectionStatus in setOf(
        ConnectionStatus.STARTING, ConnectionStatus.VALIDATING,
        ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.SOCKS_READY,
        ConnectionStatus.RECONNECTING, ConnectionStatus.STOPPING
    )
    val isError = connectionStatus == ConnectionStatus.ERROR || connectionStatus == ConnectionStatus.FAILED

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "quickConnect")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isWorking) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathingScale"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (isWorking) breathingScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "buttonScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> PvGreen
            isWorking -> PvPurple
            isError -> PvRed
            else -> PvSurfaceLight
        },
        animationSpec = tween(durationMillis = 500),
        label = "buttonColor"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = if (isConnected) 1.7f else if (isWorking) 1.4f else 1.15f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isConnected) 0.05f else 0.1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    val buttonSize = (100 * scaleFactor).dp.coerceIn(80.dp, 120.dp)

    Box(
        modifier = Modifier.size(buttonSize * 2.2f),
        contentAlignment = Alignment.Center
    ) {
        // Glow rings
        if (isWorking || isConnected) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                        alpha = glowAlpha
                    }
                    .background(
                        buttonColor.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .graphicsLayer {
                            scaleX = glowScale * 0.8f
                            scaleY = glowScale * 0.8f
                            alpha = glowAlpha * 1.5f
                        }
                        .background(
                            buttonColor.copy(alpha = 0.3f),
                            CircleShape
                        )
                )
            }
        }

        // Main button
        Surface(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .shadow(
                    elevation = if (isPressed) 4.dp else 16.dp,
                    shape = CircleShape,
                    ambientColor = buttonColor.copy(alpha = 0.5f),
                    spotColor = buttonColor
                )
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        scope.launch {
                            if (connectionStatus == ConnectionStatus.STOPPING) onRecover() else onToggle()
                        }
                    }
                ),
            color = buttonColor,
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Subtle gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.0f)
                                )
                            )
                        )
                )
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (isConnected) "Disconnect" else "Connect",
                    tint = Color.White,
                    modifier = Modifier.size(buttonSize * 0.4f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ServerListSheet — scrollable country list with search
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerListSheet(
    selectedServer: String?,
    onServerSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float = 1f
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            groupedServers
        } else {
            val q = searchQuery.lowercase()
            groupedServers.filter { group ->
                CountryNames.display(group.countryCode).lowercase().contains(q) ||
                group.countryCode.lowercase().contains(q) ||
                group.servers.any { it.name.lowercase().contains(q) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PvBackground,
        contentColor = PvTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Header
            Text(
                text = "Select Server",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                fontSize = (18 * scaleFactor).sp,
                color = PvTextPrimary
            )

            // Search bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = PvSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PvTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = PvTextPrimary,
                            fontSize = (13 * scaleFactor).sp,
                            fontWeight = FontWeight.Normal
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search countries or cities...",
                                        color = PvTextSecondary,
                                        fontSize = (13 * scaleFactor).sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = PvTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Server list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(filteredGroups) { group ->
                    val countryName = CountryNames.display(group.countryCode)
                    val totalServers = group.servers.sumOf { it.cityCount }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Select first server in group if tapped
                                group.servers.firstOrNull()?.let { onServerSelected(it.name) }
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CountryFlag(
                                countryCode = group.countryCode,
                                size = (22 * scaleFactor).dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = countryName,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PvTextPrimary,
                                    fontSize = (13 * scaleFactor).sp
                                )
                                if (group.servers.size > 1) {
                                    Text(
                                        text = group.servers.joinToString(", ") { it.name },
                                        color = PvTextSecondary,
                                        fontSize = (10 * scaleFactor).sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$totalServers server${if (totalServers != 1) "s" else ""}",
                                color = PvTextSecondary,
                                fontSize = (10 * scaleFactor).sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (group.servers.any { it.name == selectedServer }) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(PvGreen)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = PvDivider,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Helper composables
// ═══════════════════════════════════════════════════════════════════════════════

private const val TelegramChannelUrl = "https://t.me/PowerSigma"

@Composable
private fun SupportDialog(
    autoShow: Boolean,
    onJoin: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    scaleFactor: Float
) {
    Dialog(
        onDismissRequest = { if (autoShow) onSkip() else onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val strings = LocalAppStrings.current
        val isRtl = strings is StringsFa
        CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PvSurface),
                    border = BorderStroke(1.dp, PvDivider)
                ) {
                    Column(
                        modifier = Modifier.padding((20 * scaleFactor).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            strings.SUPPORT_AETHERST,
                            modifier = Modifier.fillMaxWidth(),
                            color = PvTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = (18 * scaleFactor).sp,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.material3.LocalTextStyle.current.copy(
                                textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                            )
                        )
                        Spacer(modifier = Modifier.height((10 * scaleFactor).dp))
                        Text(
                            strings.SUPPORT_DIALOG_DESC,
                            modifier = Modifier.fillMaxWidth(),
                            color = PvTextSecondary,
                            fontSize = (13 * scaleFactor).sp,
                            lineHeight = (18 * scaleFactor).sp,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.material3.LocalTextStyle.current.copy(
                                textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                            )
                        )
                        Spacer(modifier = Modifier.height((20 * scaleFactor).dp))
                        Button(
                            onClick = onJoin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((48 * scaleFactor).dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PvPurple, contentColor = Color.White)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                strings.JOIN_TELEGRAM,
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 * scaleFactor).sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
                        TextButton(
                            onClick = { if (autoShow) onSkip() else onCancel() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((42 * scaleFactor).dp)
                        ) {
                            Text(
                                if (autoShow) strings.SKIP else strings.CANCEL,
                                color = PvTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13 * scaleFactor).sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminRequiredDialog(
    onRelaunch: () -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float = 1f
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val strings = LocalAppStrings.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = (24 * scaleFactor).dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = (340 * scaleFactor).dp)
                    .fillMaxWidth()
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PvSurface),
                border = BorderStroke(1.dp, PvDivider),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding((24 * scaleFactor).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size((64 * scaleFactor).dp)
                            .clip(CircleShape)
                            .background(PvRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = PvRed,
                            modifier = Modifier.size((32 * scaleFactor).dp)
                        )
                    }

                    Spacer(modifier = Modifier.height((20 * scaleFactor).dp))

                    Text(
                        text = strings.ADMIN_REQUIRED,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PvTextPrimary,
                        fontSize = (20 * scaleFactor).sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height((12 * scaleFactor).dp))

                    Text(
                        text = strings.ADMIN_REQUIRED_DESC,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PvTextSecondary,
                        fontSize = (14 * scaleFactor).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height((32 * scaleFactor).dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy((12 * scaleFactor).dp)
                    ) {
                        Button(
                            onClick = onRelaunch,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((52 * scaleFactor).dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PvPurple,
                                contentColor = Color.White
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.RELAUNCH_AS_ADMIN,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (15 * scaleFactor).sp
                                )
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((52 * scaleFactor).dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = strings.CANCEL,
                                color = PvTextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = (15 * scaleFactor).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProxyOverlayPill(
    host: String,
    socksPort: String,
    httpPort: String,
    onHide: () -> Unit,
    onCopy: (String) -> Unit,
    scaleFactor: Float,
    psiphonEnabled: Boolean = false,
    psiphonPort: String = "3080"
) {
    val socksAddress = "$host:$socksPort"
    val httpAddress = "$host:$httpPort"
    val psiphonAddress = "$host:$psiphonPort"

    Surface(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .padding(horizontal = 8.dp)
            .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = PvPurple.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp),
        color = PvSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, PvDivider)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PvPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Dns, null, tint = PvPurple, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProxyCopyRow(label = "SOCKS5", address = socksAddress, onCopy = { onCopy(socksAddress) }, scaleFactor = scaleFactor)
                ProxyCopyRow(label = "HTTP", address = httpAddress, onCopy = { onCopy(httpAddress) }, scaleFactor = scaleFactor)
                if (psiphonEnabled) {
                    ProxyCopyRow(label = "Psiphon", address = psiphonAddress, onCopy = { onCopy(psiphonAddress) }, scaleFactor = scaleFactor)
                }
            }

            VerticalDivider(modifier = Modifier.height(36.dp), thickness = 1.dp, color = PvDivider)

            IconButton(onClick = onHide, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = PvTextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProxyCopyRow(
    label: String,
    address: String,
    onCopy: () -> Unit,
    scaleFactor: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCopy() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                color = PvPurple,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (9 * scaleFactor).sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = PvTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = (12 * scaleFactor).sp,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            tint = PvTextSecondary,
            modifier = Modifier.size((14 * scaleFactor).dp)
        )
    }
}

@Composable
fun WindowsProxyPortsCard(
    config: AetherConfig,
    onCopy: (String) -> Unit,
    scaleFactor: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PvSurface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp, vertical = (10 * scaleFactor).dp),
            verticalArrangement = Arrangement.spacedBy((6 * scaleFactor).dp)
        ) {
            Text(
                text = "PROXY PORTS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = PvTextSecondary,
                fontSize = (8.5 * scaleFactor).sp
            )
            ProxyCopyRow(
                label = "Counted",
                address = "127.0.0.1:10808 / 127.0.0.1:10809",
                onCopy = { onCopy("127.0.0.1:10808") },
                scaleFactor = scaleFactor
            )
            HorizontalDivider(color = PvDivider, thickness = 0.5.dp)
            ProxyCopyRow(
                label = "Core",
                address = "${config.socksHost}:${config.socksPort} / ${config.socksHost}:${config.httpPort}",
                onCopy = { onCopy("${config.socksHost}:${config.socksPort}") },
                scaleFactor = scaleFactor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Segmented Controls (restyled minimal)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun IosConnectionModeSegmentedControl(
    selectedMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit,
    enabled: Boolean = true,
    scaleFactor: Float = 1f
) {
    val strings = LocalAppStrings.current
    val modes = listOf(
        ConnectionMode.TUNNEL to strings.TUN_MODE,
        ConnectionMode.SYSTEM_PROXY to strings.SYSTEM_PROXY,
        ConnectionMode.PROXY_ONLY to strings.PROXY_ONLY
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = PvSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { (mode, label) ->
                val selected = mode == selectedMode
                val bg by animateColorAsState(
                    targetValue = if (selected) PvPurple else Color.Transparent,
                    animationSpec = tween(200), label = "modeBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) Color.White else PvTextSecondary,
                    animationSpec = tween(200), label = "modeText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((34 * scaleFactor).dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable(enabled = enabled) { onModeSelected(mode) }
                        .graphicsLayer { alpha = if (enabled || selected) 1f else 0.45f },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = textColor,
                        fontSize = (10 * scaleFactor).sp,
                        letterSpacing = 0.3.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PsiphonOptionsSheet (kept from original, restyled)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PsiphonOptionsSheet(
    config: AetherConfig,
    onUpdateConfig: (AetherConfig) -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strings = LocalAppStrings.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PvSurface,
        contentColor = PvTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        strings.PSIPHON_OPTIONS_TITLE,
                        fontWeight = FontWeight.Bold,
                        fontSize = (18 * scaleFactor).sp,
                        color = PvTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        strings.PSIPHON_OPTIONS_SUBTITLE_WG,
                        color = PvTextSecondary,
                        fontSize = (12 * scaleFactor).sp
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1D))
                ) {
                    Column {
                        IosPickerRow(
                            icon = Icons.Default.VpnLock,
                            iconBg = PvGreen,
                            title = strings.OUTER_PROTOCOL,
                            value = "Gool",
                            options = listOf("Gool"),
                            onOptionSelected = { onUpdateConfig(config.copy(psiphonChainOuter = "gool", protocol = AetherProtocol.GOOL)) }
                        )
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                strings.PSIPHON_SHEET_OUTER_DESC_GOOL,
                                color = PvTextSecondary,
                                fontSize = (12 * scaleFactor).sp,
                                lineHeight = (16 * scaleFactor).sp
                            )
                        }
                        if (config.psiphonEnabled) {
                            PsiphonDivider()
                            val orderOptions = listOf("Psiphon first", "Auto")
                            val orderValues = listOf("psiphon_first", "auto")
                            val currentOrder = when (config.psiphonMasqueOrder) {
                                "auto" -> "Auto"; else -> "Psiphon first"
                            }
                            IosPickerRow(
                                icon = Icons.Default.SwapHoriz,
                                iconBg = Color(0xFF30B0C7),
                                title = strings.MASQUE_ORDER,
                                value = currentOrder,
                                options = orderOptions,
                                onOptionSelected = { idx -> onUpdateConfig(config.copy(psiphonMasqueOrder = orderValues[idx])) }
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(
                                    when (config.psiphonMasqueOrder) {
                                        "auto" -> strings.PSIPHON_SHEET_ORDER_DESC_AUTO
                                        else -> strings.PSIPHON_SHEET_ORDER_DESC_PSIPHON_FIRST
                                    },
                                    color = PvTextSecondary,
                                    fontSize = (12 * scaleFactor).sp,
                                    lineHeight = (16 * scaleFactor).sp
                                )
                            }
                        }
                        val isWgFamily = true
                        if (false) {
                            PsiphonDivider()
                            val chainModes = listOf(PsiphonChainMode.AUTO, PsiphonChainMode.FALLBACK, PsiphonChainMode.ALWAYS)
                            val chainLabels = mapOf(PsiphonChainMode.AUTO to "Auto", PsiphonChainMode.FALLBACK to "Fallback", PsiphonChainMode.ALWAYS to "Always")
                            IosPickerRow(
                                icon = Icons.Default.Sync,
                                iconBg = PvPurple,
                                title = strings.PSIPHON_CHAIN_MODE,
                                value = chainLabels[config.psiphonChainMode] ?: strings.CHAIN_MODE_AUTO,
                                options = chainModes.map { chainLabels[it]!! },
                                onOptionSelected = { idx -> onUpdateConfig(config.copy(psiphonChainMode = chainModes[idx])) }
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                val modeDesc = when (config.psiphonChainMode) {
                                    PsiphonChainMode.AUTO -> strings.PSIPHON_SHEET_CHAIN_DESC_AUTO
                                    PsiphonChainMode.FALLBACK -> strings.PSIPHON_SHEET_CHAIN_DESC_FALLBACK
                                    PsiphonChainMode.ALWAYS -> strings.PSIPHON_SHEET_CHAIN_DESC_ALWAYS
                                }
                                Text(modeDesc, color = PvTextSecondary, fontSize = (12 * scaleFactor).sp, lineHeight = (16 * scaleFactor).sp)
                            }
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(strings.PSIPHON_SHEET_WG_ALWAYS_VIA, color = PvTextSecondary, fontSize = (12 * scaleFactor).sp, lineHeight = (16 * scaleFactor).sp)
                            }
                        }
                        PsiphonDivider()
                        val availableRegions by PsiphonEgressRegistry.availableRegions.collectAsStateWithLifecycle()
                        val selectedRegion = config.psiphonEgressRegion.trim().uppercase()
                        val regionCodes = buildList {
                            add("")
                            addAll(availableRegions)
                            if (selectedRegion.isNotEmpty() && selectedRegion !in availableRegions) add(selectedRegion)
                        }
                        val regionOptions = regionCodes.map { CountryNames.label(it) }
                        IosPickerRow(
                            icon = Icons.Default.Public,
                            iconBg = Color(0xFF30B0C7),
                            title = strings.EXIT_COUNTRY,
                            value = CountryNames.label(selectedRegion),
                            options = regionOptions,
                            onOptionSelected = { idx -> onUpdateConfig(config.copy(psiphonEgressRegion = regionCodes[idx])) }
                        )
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(strings.PSIPHON_SHEET_EXIT_AUTO, color = PvTextSecondary, fontSize = (12 * scaleFactor).sp, lineHeight = (16 * scaleFactor).sp)
                        }
                        if (isWgFamily) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Text(strings.PSIPHON_SHEET_EGRESS_WARN_WG, color = Color(0xFFFFCC00), fontSize = (11 * scaleFactor).sp, lineHeight = (15 * scaleFactor).sp)
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1D))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(strings.HOW_IT_WORKS, fontWeight = FontWeight.Bold, color = PvTextPrimary, fontSize = (14 * scaleFactor).sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            strings.PSIPHON_SHEET_HOW_GOOL,
                            color = PvTextSecondary,
                            fontSize = (12 * scaleFactor).sp,
                            lineHeight = (17 * scaleFactor).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PsiphonDivider() {
    HorizontalDivider(color = PvDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 50.dp))
}

// ═══════════════════════════════════════════════════════════════════════════════
// Helper functions
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    fun pad(n: Long) = if (n < 10) "0$n" else n.toString()
    return "${pad(h)}:${pad(m)}:${pad(s)}"
}

private fun formatTrafficBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0).coerceAtMost(9_000_000_000_000_000L)
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = safeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val roundedValue = (value * 100).toLong() / 100.0
    return if (unitIndex == 0) {
        "$safeBytes ${units[unitIndex]}"
    } else {
        val formatted = if (roundedValue >= 100) "${roundedValue.toLong()}" else "$roundedValue"
        "$formatted ${units[unitIndex]}"
    }
}

private fun formatSpeedValue(bytesPerSec: Double): String {
    return when {
        bytesPerSec >= 1024.0 * 1024.0 * 1024.0 * 1024.0 -> "${"%.1f".format(bytesPerSec / (1024.0 * 1024.0 * 1024.0 * 1024.0))} TB/s"
        bytesPerSec >= 1024.0 * 1024.0 * 1024.0 -> "${"%.1f".format(bytesPerSec / (1024.0 * 1024.0 * 1024.0))} GB/s"
        bytesPerSec >= 1024.0 * 1024.0 -> "${"%.1f".format(bytesPerSec / (1024.0 * 1024.0))} MB/s"
        bytesPerSec >= 1024.0 -> "${"%.0f".format(bytesPerSec / 1024.0)} KB/s"
        else -> "${"%.0f".format(bytesPerSec)} B/s"
    }
}


