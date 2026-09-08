package io.github.immaghzbad.aetherst.shared.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.isDesktop
import io.github.immaghzbad.aetherst.shared.data.AutoDetectRepository
import io.github.immaghzbad.aetherst.shared.data.DnsBenchmarkPhase
import io.github.immaghzbad.aetherst.shared.data.DnsBenchmarkRepository
import io.github.immaghzbad.aetherst.shared.data.DnsProbeResult
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings
import io.github.immaghzbad.aetherst.shared.model.AetherNoise
import io.github.immaghzbad.aetherst.shared.model.AetherProtocol
import io.github.immaghzbad.aetherst.shared.model.AetherScanMode
import io.github.immaghzbad.aetherst.shared.model.AutoDetectPhase
import io.github.immaghzbad.aetherst.shared.model.AutoDetectResult
import io.github.immaghzbad.aetherst.shared.model.AutoDetectState
import io.github.immaghzbad.aetherst.shared.model.MtuProbeResult
import io.github.immaghzbad.aetherst.shared.model.NoiseProbeResult
import io.github.immaghzbad.aetherst.shared.model.ProbeStatus
import io.github.immaghzbad.aetherst.shared.model.ProtocolProbeResult
import io.github.immaghzbad.aetherst.shared.model.ScanModeProbeResult
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette

private val IosCardBg = AppPalette.surfaceRaised
private val IosGroupBg = AppPalette.divider
private val IosSecondaryLabel = AppPalette.textSecondary
private val IosActiveGreen = AppPalette.statusConnected
private val IosActiveBlue = AppPalette.accent
private val IosErrorRed = AppPalette.statusError
private val IosAmber = AppPalette.statusScanning

@Composable
fun AutoDetectScreen(
    onBack: () -> Unit,
    onApplyResult: (AutoDetectResult) -> Unit,
    platformContext: PlatformContext,
    bottomContentPadding: Dp = 0.dp,
    initialSection: Int = 0,
    tunnelDns: String = "",
    onApplyDns: (String) -> Unit = {},
    onCopy: (String) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val state by AutoDetectRepository.state.collectAsStateWithLifecycle()
    var selectedTab by remember(initialSection) { androidx.compose.runtime.mutableIntStateOf(initialSection.coerceIn(0, 1)) }

    val retest: () -> Unit = {
        AutoDetectRepository.reset()
        AutoDetectRepository.startDetection(platformContext)
    }

    val startScan: () -> Unit = {
        AutoDetectRepository.reset()
        AutoDetectRepository.startDetection(platformContext)
    }

    val cancelScan: () -> Unit = {
        AutoDetectRepository.cancel()
    }

    DisposableEffect(Unit) {
        onDispose {
            AutoDetectRepository.cancel()
            DnsBenchmarkRepository.cancel()
        }
    }

    val scaleFactor = remember { 1f }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (8 * scaleFactor).dp,
                    end = (12 * scaleFactor).dp,
                    top = if (isDesktop) 12.dp else 36.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                AutoDetectRepository.cancel()
                DnsBenchmarkRepository.cancel()
                onBack()
            }, modifier = Modifier.size((40 * scaleFactor).dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size((24 * scaleFactor).dp)
                )
            }
            Spacer(modifier = Modifier.width((4 * scaleFactor).dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.AUTODETECT_TITLE,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (26 * scaleFactor).sp,
                    lineHeight = (30 * scaleFactor).sp
                )
                Text(
                    text = strings.AUTODETECT_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = IosSecondaryLabel,
                    fontSize = (12 * scaleFactor).sp
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scaleFactor).dp).background(IosCardBg, RoundedCornerShape(10.dp)).padding(2.dp)) {
            listOf(strings.AUTODETECT_TAB_CONNECTION, strings.AUTODETECT_TAB_DNS).forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSelected) IosActiveBlue else Color.Transparent).clickable { selectedTab = index }.padding(vertical = (8 * scaleFactor).dp), contentAlignment = Alignment.Center) {
                    Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else IosSecondaryLabel, fontSize = (13 * scaleFactor).sp)
                }
            }
        }
        Spacer(modifier = Modifier.height((8 * scaleFactor).dp))

        if (selectedTab == 0) {
            ConnectionSection(state, startScan, cancelScan, retest, onApplyResult, platformContext, scaleFactor, bottomContentPadding)
        } else {
            DnsSection(tunnelDns, onApplyDns, onCopy, scaleFactor, bottomContentPadding)
        }
    }
}

@Composable
private fun ConnectionSection(
    state: AutoDetectState,
    startScan: () -> Unit,
    cancelScan: () -> Unit,
    retest: () -> Unit,
    onApplyResult: (AutoDetectResult) -> Unit,
    platformContext: PlatformContext,
    scaleFactor: Float,
    bottomContentPadding: Dp
) {
    val strings = LocalAppStrings.current
    val isRunning = state.phase != AutoDetectPhase.IDLE && state.phase != AutoDetectPhase.COMPLETE && state.phase != AutoDetectPhase.ERROR
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = (16 * scaleFactor).dp,
            end = (16 * scaleFactor).dp,
            bottom = bottomContentPadding + (24 * scaleFactor).dp,
            top = (4 * scaleFactor).dp
        ),
        verticalArrangement = Arrangement.spacedBy((12 * scaleFactor).dp)
    ) {
        if (state.phase == AutoDetectPhase.IDLE) {
            item {
                ConnectionStartCard(startScan, scaleFactor)
            }
        }
        if (isRunning) {
            item {
                AutoDetectProgressCard(state, scaleFactor)
            }
            item {
                Button(
                    onClick = cancelScan,
                    modifier = Modifier.fillMaxWidth().height((48 * scaleFactor).dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosErrorRed, contentColor = Color.White)
                ) {
                    Text(strings.AUTODETECT_CANCEL_SCAN, fontWeight = FontWeight.Bold, fontSize = (14 * scaleFactor).sp)
                }
            }
        }

        if (state.currentStep.isNotEmpty() && state.phase != AutoDetectPhase.COMPLETE && state.phase != AutoDetectPhase.IDLE) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IosActiveBlue.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding((14 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size((18 * scaleFactor).dp),
                            color = IosActiveBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                        Text(
                            text = localizedAutoDetectStep(state.currentStep, strings),
                            color = IosActiveBlue,
                            fontSize = (13 * scaleFactor).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (state.phase != AutoDetectPhase.IDLE && state.phase != AutoDetectPhase.ERROR) {
            item {
                SectionHeader(strings.AUTODETECT_SECTION_NETWORK_ENV, scaleFactor)
                NetworkFingerprintCard(state, scaleFactor)
            }
        }

        if (state.protocolResults.isNotEmpty()) {
            item {
                SectionHeader(strings.AUTODETECT_SECTION_PROTOCOL_LATENCY, scaleFactor)
            }
            items(state.protocolResults) { result ->
                ProtocolProbeRow(result, scaleFactor)
            }
        }

        val completedResult = state.finalResult
        if (state.phase == AutoDetectPhase.COMPLETE && completedResult != null && state.protocolResults.any { it.status == ProbeStatus.SUCCESS }) {
            item {
                SectionHeader(strings.AUTODETECT_SECTION_PROTOCOL_RESULTS, scaleFactor)
                Text(
                    text = strings.AUTODETECT_RANKED_BY_QUALITY,
                    color = IosSecondaryLabel,
                    fontSize = (11 * scaleFactor).sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = (8 * scaleFactor).dp)
                )
            }
            val base = completedResult
            items(
                state.protocolResults
                    .filter { it.status == ProbeStatus.SUCCESS }
                    .sortedBy { it.latencyMs }
            ) { result ->
                ProtocolResultRankRow(
                    rank = state.protocolResults.filter { it.status == ProbeStatus.SUCCESS }.sortedBy { it.latencyMs }.indexOf(result) + 1,
                    probe = result,
                    onApply = { onApplyResult(buildResultForProtocol(result.protocol, base)) },
                    scaleFactor = scaleFactor
                )
            }
            items(state.protocolResults.filter { it.status != ProbeStatus.SUCCESS }) { result ->
                ProtocolProbeRow(result, scaleFactor)
            }
        }

        if (state.mtuResult.status != ProbeStatus.IDLE) {
            item {
                SectionHeader(strings.AUTODETECT_SECTION_MTU, scaleFactor)
                MtuProbeRow(state.mtuResult, scaleFactor)
            }
        }

        if (state.noiseResults.isNotEmpty()) {
            item {
                SectionHeader(strings.AUTODETECT_SECTION_OBFUSCATION, scaleFactor)
            }
            items(state.noiseResults) { result ->
                NoiseProbeRow(result, scaleFactor)
            }
        }

        if (state.scanModeResults.isNotEmpty()) {
            item {
                SectionHeader(strings.AUTODETECT_SECTION_SCAN_STRATEGIES, scaleFactor)
            }
            items(state.scanModeResults) { result ->
                ScanModeProbeRow(result, scaleFactor)
            }
        }

        if (state.phase == AutoDetectPhase.COMPLETE && completedResult != null) {
            item {
                SectionHeader(strings.AUTODETECT_SECTION_RECOMMENDED, scaleFactor)
                AutoDetectFinalResult(completedResult, onApplyResult, retest, scaleFactor)
            }
        }

        if (state.phase == AutoDetectPhase.ERROR) {
            item {
                ErrorCard(state.error, retest, scaleFactor)
            }
        }

        item {
            Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, scaleFactor: Float) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = IosSecondaryLabel,
        fontSize = (10 * scaleFactor).sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = (4 * scaleFactor).dp)
    )
}

@Composable
private fun AutoDetectProgressCard(state: AutoDetectState, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBg)
    ) {
        Column(modifier = Modifier.padding((16 * scaleFactor).dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.AUTODETECT_SCANNING, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (14 * scaleFactor).sp)
                Text("${state.progressPercent}%", color = IosActiveBlue, fontWeight = FontWeight.ExtraBold, fontSize = (14 * scaleFactor).sp)
            }
            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((6 * scaleFactor).dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = IosActiveBlue,
                trackColor = IosGroupBg
            )
        }
    }
}

@Composable
private fun NetworkFingerprintCard(state: AutoDetectState, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val fingerprint = state.finalResult?.networkFingerprint ?: state.liveFingerprint
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (strings is io.github.immaghzbad.aetherst.shared.i18n.StringsFa) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = IosCardBg)
        ) {
            Column(modifier = Modifier.padding((14 * scaleFactor).dp), verticalArrangement = Arrangement.spacedBy((8 * scaleFactor).dp)) {
                FingerprintRow(
                    label = strings.AUTODETECT_PILL_NETWORK,
                    value = when (fingerprint?.networkType) {
                        "open" -> strings.AUTODETECT_NETWORK_OPEN
                        "restricted" -> strings.AUTODETECT_NETWORK_RESTRICTED
                        else -> "—"
                    },
                    color = if (fingerprint?.supportsDPI == true) IosAmber else IosActiveGreen,
                    scaleFactor = scaleFactor
                )
                FingerprintRow(
                    label = strings.AUTODETECT_PILL_DPI_DETECTED,
                    value = when (fingerprint?.supportsDPI) { true -> strings.AUTODETECT_YES; false -> strings.AUTODETECT_NO; null -> "—" },
                    color = if (fingerprint?.supportsDPI == true) IosErrorRed else IosActiveGreen,
                    scaleFactor = scaleFactor
                )
                FingerprintRow(
                    label = strings.AUTODETECT_PILL_IPV6,
                    value = when (fingerprint?.supportsIPv6) { true -> strings.AUTODETECT_YES; false -> strings.AUTODETECT_NO; null -> "—" },
                    color = if (fingerprint?.supportsIPv6 == true) IosActiveGreen else IosSecondaryLabel,
                    scaleFactor = scaleFactor
                )
                FingerprintRow(
                    label = "UDP",
                    value = when (fingerprint?.supportsUDP) { true -> strings.AUTODETECT_YES; false -> strings.AUTODETECT_NO; null -> "—" },
                    color = if (fingerprint?.supportsUDP == true) IosActiveGreen else IosErrorRed,
                    scaleFactor = scaleFactor
                )
                FingerprintRow(
                    label = strings.AUTODETECT_PILL_ISP,
                    value = fingerprint?.carrierOrIsp?.ifBlank { "—" } ?: "—",
                    color = IosActiveBlue,
                    scaleFactor = scaleFactor
                )
                FingerprintRow(
                    label = strings.AUTODETECT_PILL_IP,
                    value = fingerprint?.ipAddress?.ifBlank { "—" } ?: "—",
                    color = IosActiveBlue,
                    scaleFactor = scaleFactor
                )
            }
        }
    }
}

@Composable
private fun FingerprintRow(label: String, value: String, color: Color, scaleFactor: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = IosGroupBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp, vertical = (10 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = IosSecondaryLabel,
                fontSize = (10 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                color = color,
                fontSize = (12 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = (12 * scaleFactor).dp)
            )
        }
    }
}

@Suppress("UNUSED")
@Composable
private fun InfoPill(label: String, value: String, color: Color, modifier: Modifier = Modifier, scaleFactor: Float) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = IosGroupBg
    ) {
        Column(
            modifier = Modifier.padding(horizontal = (10 * scaleFactor).dp, vertical = (8 * scaleFactor).dp)
        ) {
            Text(
                text = label,
                color = IosSecondaryLabel,
                fontSize = (8 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = (12 * scaleFactor).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProtocolProbeRow(result: ProtocolProbeResult, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val statusColor = when (result.status) {
        ProbeStatus.SUCCESS -> IosActiveGreen
        ProbeStatus.FAILED -> IosErrorRed
        ProbeStatus.RUNNING -> IosAmber
        ProbeStatus.SKIPPED -> IosSecondaryLabel
        ProbeStatus.IDLE -> IosSecondaryLabel
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding((14 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size((10 * scaleFactor).dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.protocol.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (14 * scaleFactor).sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    when (result.status) {
                        ProbeStatus.SUCCESS -> Text(
                            text = strings.AUTODETECT_RTT.format(result.latencyMs, result.latencyMs),
                            color = IosActiveGreen,
                            fontSize = (11 * scaleFactor).sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        ProbeStatus.FAILED -> Text(
                            text = result.error ?: strings.AUTODETECT_CONNECTION_FAILED,
                            color = IosErrorRed,
                            fontSize = (11 * scaleFactor).sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        ProbeStatus.RUNNING -> Text(
                            text = strings.AUTODETECT_MEASURING_LATENCY,
                            color = IosAmber,
                            fontSize = (11 * scaleFactor).sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        ProbeStatus.SKIPPED -> Text(
                            text = result.error ?: strings.AUTODETECT_SKIPPED,
                            color = IosSecondaryLabel,
                            fontSize = (11 * scaleFactor).sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        else -> {}
                    }
                }
            }

            when (result.status) {
                ProbeStatus.SUCCESS -> Icon(
                    Icons.Default.CheckCircle, null,
                    tint = IosActiveGreen,
                    modifier = Modifier.size((20 * scaleFactor).dp)
                )
                ProbeStatus.FAILED -> Icon(
                    Icons.Default.Error, null,
                    tint = IosErrorRed,
                    modifier = Modifier.size((20 * scaleFactor).dp)
                )
                ProbeStatus.RUNNING -> CircularProgressIndicator(
                    modifier = Modifier.size((20 * scaleFactor).dp),
                    strokeWidth = 2.dp, color = IosAmber
                )
                ProbeStatus.SKIPPED -> Text(
                    strings.AUTODETECT_SKIP, color = IosSecondaryLabel,
                    fontSize = (10 * scaleFactor).sp, fontWeight = FontWeight.Bold
                )
                ProbeStatus.IDLE -> {}
            }
        }
    }
}

@Composable
private fun MtuProbeRow(result: MtuProbeResult, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val statusColor = when (result.status) {
        ProbeStatus.SUCCESS -> IosActiveGreen
        ProbeStatus.FAILED -> IosErrorRed
        else -> IosSecondaryLabel
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding((14 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size((10 * scaleFactor).dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                Column {
                    Text(strings.AUTODETECT_PATH_MTU, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (14 * scaleFactor).sp)
                    if (result.status == ProbeStatus.SUCCESS) {
                        Text(
                            text = strings.AUTODETECT_OPTIMAL.format(result.discoveredMtu, result.rawPathMtu),
                            color = IosActiveGreen, fontSize = (11 * scaleFactor).sp
                        )
                    } else if (result.status == ProbeStatus.FAILED) {
                        Text(strings.AUTODETECT_SAFE_DEFAULT, color = IosErrorRed, fontSize = (11 * scaleFactor).sp)
                    }
                }
            }
            when (result.status) {
                ProbeStatus.SUCCESS -> Icon(Icons.Default.CheckCircle, null, tint = IosActiveGreen, modifier = Modifier.size((20 * scaleFactor).dp))
                ProbeStatus.FAILED -> Icon(Icons.Default.Error, null, tint = IosErrorRed, modifier = Modifier.size((20 * scaleFactor).dp))
                else -> {}
            }
        }
    }
}

@Composable
private fun NoiseProbeRow(result: NoiseProbeResult, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val statusColor = if (result.status == ProbeStatus.SUCCESS && result.effective) IosActiveGreen
    else if (result.status == ProbeStatus.FAILED) IosErrorRed
    else IosSecondaryLabel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(IosCardBg)
            .padding(horizontal = (14 * scaleFactor).dp, vertical = (10 * scaleFactor).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size((8 * scaleFactor).dp).clip(CircleShape).background(statusColor))
            Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
            Text(result.noise.displayName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = (13 * scaleFactor).sp)
        }
        if (result.effective) {
            Text(strings.AUTODETECT_EFFECTIVE, color = IosActiveGreen, fontWeight = FontWeight.ExtraBold, fontSize = (9 * scaleFactor).sp, letterSpacing = 0.5.sp)
        } else if (result.status == ProbeStatus.SUCCESS) {
            Text(strings.AUTODETECT_WEAK, color = IosSecondaryLabel, fontWeight = FontWeight.Medium, fontSize = (9 * scaleFactor).sp)
        }
    }
}

@Composable
private fun ScanModeProbeRow(result: ScanModeProbeResult, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val statusColor = if (result.status == ProbeStatus.SUCCESS && result.gatewayFound) IosActiveGreen else IosSecondaryLabel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(IosCardBg)
            .padding(horizontal = (14 * scaleFactor).dp, vertical = (10 * scaleFactor).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size((8 * scaleFactor).dp).clip(CircleShape).background(statusColor))
            Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
            Text(result.scanMode.name.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Medium, fontSize = (13 * scaleFactor).sp)
        }
        if (result.gatewayFound) {
            Text(strings.AUTODETECT_VERIFIED, color = IosActiveGreen, fontWeight = FontWeight.ExtraBold, fontSize = (9 * scaleFactor).sp, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun AutoDetectFinalResult(
    result: AutoDetectResult,
    onApplyResult: (AutoDetectResult) -> Unit,
    onRetest: () -> Unit,
    scaleFactor: Float
) {
    val strings = LocalAppStrings.current
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = (8 * scaleFactor).dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = IosActiveGreen.copy(alpha = 0.4f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBg),
        border = BorderStroke((1.5 * scaleFactor).dp, IosActiveGreen.copy(alpha = glowAlpha))
    ) {
        Column(
            modifier = Modifier.padding((20 * scaleFactor).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size((56 * scaleFactor).dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(IosActiveGreen.copy(alpha = 0.3f), IosActiveGreen.copy(alpha = 0.05f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = IosActiveGreen, modifier = Modifier.size((28 * scaleFactor).dp))
            }

            Spacer(modifier = Modifier.height((14 * scaleFactor).dp))

            Text(
                strings.AUTODETECT_OPTIMAL_FOUND,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = (18 * scaleFactor).sp
            )

            Spacer(modifier = Modifier.height((4 * scaleFactor).dp))

            val confidencePercent = (result.confidence * 100).toInt()
            Text(
                strings.AUTODETECT_CONFIDENCE.format(confidencePercent),
                color = if (result.confidence > 0.7f) IosActiveGreen else IosAmber,
                fontWeight = FontWeight.Bold,
                fontSize = (13 * scaleFactor).sp
            )

            Spacer(modifier = Modifier.height((16 * scaleFactor).dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IosGroupBg)
            ) {
                Column(modifier = Modifier.padding((14 * scaleFactor).dp)) {
                    RecommendationRow(strings.AUTODETECT_LABEL_PROTOCOL, result.recommendedProtocol.displayName, scaleFactor)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                    RecommendationRow(strings.AUTODETECT_LABEL_OBFUSCATION, result.recommendedNoise.displayName, scaleFactor)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                    RecommendationRow(strings.AUTODETECT_LABEL_SCAN_MODE, result.recommendedScanMode.name.lowercase().replaceFirstChar { it.uppercase() }, scaleFactor)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                    RecommendationRow(strings.AUTODETECT_LABEL_MTU, "${result.recommendedMtu} bytes", scaleFactor)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                    RecommendationRow(strings.AUTODETECT_LABEL_NETWORK_STACK, result.recommendedIpMode.displayName, scaleFactor)
                    if (result.recommendedH2Mode) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                        RecommendationRow(strings.AUTODETECT_LABEL_HTTP2_FALLBACK, strings.AUTODETECT_ENABLED, scaleFactor)
                    }
                    if (result.recommendedEch) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                        RecommendationRow(strings.AUTODETECT_LABEL_ECH, strings.AUTODETECT_ENABLED, scaleFactor)
                    }
                    if (result.recommendedFragment) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                        RecommendationRow(strings.AUTODETECT_LABEL_PACKET_FRAGMENT, strings.AUTODETECT_ENABLED, scaleFactor)
                    }
                }
            }

            Spacer(modifier = Modifier.height((20 * scaleFactor).dp))

            Button(
                onClick = { onApplyResult(result) },
                modifier = Modifier.fillMaxWidth().height((52 * scaleFactor).dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosActiveGreen, contentColor = Color.White)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.AUTODETECT_APPLY_CONFIGURATION, fontWeight = FontWeight.Bold, fontSize = (15 * scaleFactor).sp)
                }
            }

            Spacer(modifier = Modifier.height((8 * scaleFactor).dp))

            OutlinedButton(
                onClick = onRetest,
                modifier = Modifier.fillMaxWidth().height((44 * scaleFactor).dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IosSecondaryLabel),
                border = BorderStroke(1.dp, IosSecondaryLabel.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.AUTODETECT_RETEST_NETWORK, fontWeight = FontWeight.SemiBold, fontSize = (13 * scaleFactor).sp)
            }
        }
    }
}

@Composable
private fun RecommendationRow(label: String, value: String, scaleFactor: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = IosSecondaryLabel, fontSize = (12 * scaleFactor).sp, fontWeight = FontWeight.Medium)
        Text(value, color = Color.White, fontSize = (13 * scaleFactor).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorCard(error: String?, onRetry: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosErrorRed.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding((16 * scaleFactor).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = IosErrorRed, modifier = Modifier.size((32 * scaleFactor).dp))
            Spacer(modifier = Modifier.height((12 * scaleFactor).dp))
            Text(strings.AUTODETECT_DETECTION_FAILED, color = IosErrorRed, fontWeight = FontWeight.Bold, fontSize = (16 * scaleFactor).sp)
            Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
            Text(error ?: strings.AUTODETECT_UNKNOWN_ERROR, color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = IosErrorRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height((48 * scaleFactor).dp)
            ) {
                Text(strings.AUTODETECT_RETRY_DETECTION, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ConnectionStartCard(onStart: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBg)
    ) {
        Column(modifier = Modifier.padding((20 * scaleFactor).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size((56 * scaleFactor).dp).clip(CircleShape).background(IosActiveBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = IosActiveBlue, modifier = Modifier.size((28 * scaleFactor).dp))
            }
            Spacer(modifier = Modifier.height((14 * scaleFactor).dp))
            Text(strings.AUTODETECT_START_DESC, color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height((52 * scaleFactor).dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosActiveBlue, contentColor = Color.White)
            ) {
                Text(strings.AUTODETECT_START_SCAN, fontWeight = FontWeight.Bold, fontSize = (15 * scaleFactor).sp)
            }
        }
    }
}

@Composable
private fun DnsSection(tunnelDns: String, onApplyDns: (String) -> Unit, onCopy: (String) -> Unit, scaleFactor: Float, bottomContentPadding: Dp) {
    val strings = LocalAppStrings.current
    val dnsState by DnsBenchmarkRepository.state.collectAsStateWithLifecycle()
    var customText by remember(dnsState.customDns) { mutableStateOf(dnsState.customDns) }
    var applyDismissed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(dnsState.bestDnsList) {
        if (dnsState.bestDnsList.isNotEmpty()) applyDismissed = false
    }
    val isRunning = dnsState.phase == DnsBenchmarkPhase.RUNNING
    val hasSelection = DnsBenchmarkRepository.activeEntries().isNotEmpty()
    val showResultsOnly = !isRunning && dnsState.results.isNotEmpty()
    val sortedResults = dnsState.results.sortedBy { if (it.status == ProbeStatus.SUCCESS) it.medianMs else Long.MAX_VALUE }
    val copyText = if (dnsState.bestDnsList.isNotEmpty()) dnsState.bestDnsList else sortedResults.filter { it.status == ProbeStatus.SUCCESS }.map { it.ip }.joinToString(",")
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (isRunning) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(
                        start = (16 * scaleFactor).dp,
                        end = (16 * scaleFactor).dp,
                        top = (4 * scaleFactor).dp,
                        bottom = (12 * scaleFactor).dp
                    ),
                    verticalArrangement = Arrangement.spacedBy((12 * scaleFactor).dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = IosCardBg)) {
                        Column(modifier = Modifier.padding((16 * scaleFactor).dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(strings.DNS_SCANNING, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (14 * scaleFactor).sp)
                                Text("${dnsState.progressPercent}%", color = IosActiveBlue, fontWeight = FontWeight.ExtraBold, fontSize = (14 * scaleFactor).sp)
                            }
                            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))
                            LinearProgressIndicator(
                                progress = { dnsState.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth().height((6 * scaleFactor).dp).clip(RoundedCornerShape(3.dp)),
                                color = IosActiveBlue,
                                trackColor = IosGroupBg
                            )
                            if (dnsState.currentStep.isNotEmpty()) {
                                Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
                                Text(dnsState.currentStep, color = IosSecondaryLabel, fontSize = (11 * scaleFactor).sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = (16 * scaleFactor).dp,
                        end = (16 * scaleFactor).dp,
                        top = (4 * scaleFactor).dp,
                        bottom = (12 * scaleFactor).dp
                    ),
                    verticalArrangement = Arrangement.spacedBy((12 * scaleFactor).dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = IosCardBg)) {
                            Row(modifier = Modifier.fillMaxWidth().padding((16 * scaleFactor).dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(strings.DNS_INCLUDE_IPV6, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (14 * scaleFactor).sp)
                                    Text(strings.DNS_INCLUDE_IPV6_SUB, color = IosSecondaryLabel, fontSize = (11 * scaleFactor).sp)
                                }
                                Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                Switch(
                                    checked = dnsState.includeIpv6,
                                    onCheckedChange = { DnsBenchmarkRepository.setIncludeIpv6(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IosActiveGreen, uncheckedThumbColor = Color.White, uncheckedTrackColor = IosSecondaryLabel.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                    if (tunnelDns.isNotBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = IosGroupBg)) {
                                Column(modifier = Modifier.padding((14 * scaleFactor).dp)) {
                                    Text(strings.DNS_CURRENT_TUNNEL, color = IosSecondaryLabel, fontSize = (10 * scaleFactor).sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(tunnelDns, color = IosActiveBlue, fontSize = (12 * scaleFactor).sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    if (!showResultsOnly) {
                        item {
                            SectionHeader(strings.DNS_SECTION_GLOBAL, scaleFactor)
                        }
                        val visibleGlobal = DnsBenchmarkRepository.globalServers.filter { dnsState.includeIpv6 || !it.isIpv6 }
                        items(visibleGlobal, key = { it.id }) { entry ->
                            DnsEntryRow(entry.id, entry.name, entry.ip, entry.isIpv6, dnsState.enabledIds.contains(entry.id), { DnsBenchmarkRepository.toggleServer(entry.id) }, scaleFactor)
                        }
                        item {
                            SectionHeader(strings.DNS_SECTION_IRAN, scaleFactor)
                        }
                        items(DnsBenchmarkRepository.iranServers, key = { it.id }) { entry ->
                            DnsEntryRow(entry.id, entry.name, entry.ip, entry.isIpv6, dnsState.enabledIds.contains(entry.id), { DnsBenchmarkRepository.toggleServer(entry.id) }, scaleFactor)
                        }
                        item {
                            SectionHeader(strings.DNS_SECTION_CUSTOM, scaleFactor)
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = IosCardBg)) {
                                Column(modifier = Modifier.padding((14 * scaleFactor).dp)) {
                                    OutlinedTextField(
                                        value = customText,
                                        onValueChange = {
                                            customText = it
                                            DnsBenchmarkRepository.setCustomDns(it)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(strings.DNS_CUSTOM_PLACEHOLDER, color = IosSecondaryLabel.copy(alpha = 0.6f)) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = (13 * scaleFactor).sp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IosActiveBlue, unfocusedBorderColor = IosSecondaryLabel.copy(alpha = 0.3f), cursorColor = IosActiveBlue),
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                    if (showResultsOnly) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SectionHeader(strings.DNS_BEST_RESULT, scaleFactor)
                                }
                                OutlinedButton(
                                    onClick = { DnsBenchmarkRepository.reset() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IosSecondaryLabel),
                                    border = BorderStroke(1.dp, IosSecondaryLabel.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(strings.EDIT, fontWeight = FontWeight.Bold, fontSize = (12 * scaleFactor).sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { if (copyText.isNotEmpty()) onCopy(copyText) },
                                    enabled = copyText.isNotEmpty(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IosActiveBlue),
                                    border = BorderStroke(1.dp, IosActiveBlue.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(strings.LOGS_COPY, fontWeight = FontWeight.Bold, fontSize = (12 * scaleFactor).sp)
                                }
                            }
                        }
                        items(sortedResults, key = { it.id + it.ip }) { result ->
                            DnsResultRow(result, { onCopy(result.ip) }, scaleFactor)
                        }
                    }
                    if (dnsState.phase == DnsBenchmarkPhase.COMPLETE && dnsState.bestDnsList.isNotEmpty() && !applyDismissed) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = IosActiveGreen.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, IosActiveGreen.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding((16 * scaleFactor).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(strings.DNS_APPLY_TITLE, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (15 * scaleFactor).sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height((6 * scaleFactor).dp))
                                    Text(strings.DNS_APPLY_DESC, color = IosSecondaryLabel, fontSize = (12 * scaleFactor).sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
                                    Text(dnsState.bestDnsList, color = IosActiveGreen, fontWeight = FontWeight.Bold, fontSize = (13 * scaleFactor).sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height((14 * scaleFactor).dp))
                                    Button(
                                        onClick = {
                                            onApplyDns(dnsState.bestDnsList)
                                            applyDismissed = true
                                        },
                                        modifier = Modifier.fillMaxWidth().height((50 * scaleFactor).dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IosActiveGreen, contentColor = Color.White)
                                    ) {
                                        Text(strings.DNS_APPLY_YES, fontWeight = FontWeight.Bold, fontSize = (14 * scaleFactor).sp)
                                    }
                                    Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
                                    OutlinedButton(
                                        onClick = { applyDismissed = true },
                                        modifier = Modifier.fillMaxWidth().height((44 * scaleFactor).dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IosSecondaryLabel),
                                        border = BorderStroke(1.dp, IosSecondaryLabel.copy(alpha = 0.4f))
                                    ) {
                                        Text(strings.DNS_APPLY_NO, fontWeight = FontWeight.SemiBold, fontSize = (13 * scaleFactor).sp)
                                    }
                                }
                            }
                        }
                    }
                    if (dnsState.phase == DnsBenchmarkPhase.ERROR) {
                        item {
                            val message = if (dnsState.error == "empty") strings.DNS_EMPTY_SELECTION else (dnsState.error ?: strings.AUTODETECT_UNKNOWN_ERROR)
                            ErrorCard(message, { DnsBenchmarkRepository.reset() }, scaleFactor)
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(
                start = (16 * scaleFactor).dp,
                end = (16 * scaleFactor).dp,
                top = (4 * scaleFactor).dp,
                bottom = bottomContentPadding + (12 * scaleFactor).dp
            )
        ) {
            if (isRunning) {
                Button(
                    onClick = { DnsBenchmarkRepository.cancel() },
                    modifier = Modifier.fillMaxWidth().height((52 * scaleFactor).dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosErrorRed, contentColor = Color.White)
                ) {
                    Text(strings.AUTODETECT_CANCEL_SCAN, fontWeight = FontWeight.Bold, fontSize = (15 * scaleFactor).sp)
                }
            } else {
                Button(
                    onClick = { DnsBenchmarkRepository.startScan() },
                    enabled = hasSelection,
                    modifier = Modifier.fillMaxWidth().height((52 * scaleFactor).dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosActiveGreen, contentColor = Color.White, disabledContainerColor = IosSecondaryLabel.copy(alpha = 0.3f))
                ) {
                    Text(if (dnsState.results.isEmpty()) strings.DNS_SCAN_START else strings.DNS_RETEST, fontWeight = FontWeight.Bold, fontSize = (15 * scaleFactor).sp)
                }
                if (!hasSelection) {
                    Spacer(modifier = Modifier.height((6 * scaleFactor).dp))
                    Text(strings.DNS_EMPTY_SELECTION, color = IosErrorRed, fontSize = (12 * scaleFactor).sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DnsEntryRow(id: String, name: String, ip: String, isIpv6: Boolean, enabled: Boolean, onToggle: () -> Unit, scaleFactor: Float) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) IosCardBg else IosCardBg.copy(alpha = 0.5f)),
        border = if (enabled) BorderStroke(1.dp, IosActiveBlue.copy(alpha = 0.35f)) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding((14 * scaleFactor).dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size((10 * scaleFactor).dp).clip(CircleShape).background(if (enabled) IosActiveGreen else IosSecondaryLabel.copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (14 * scaleFactor).sp)
                    if (isIpv6) {
                        Surface(shape = RoundedCornerShape(6.dp), color = IosActiveBlue.copy(alpha = 0.2f)) {
                            Text("IPv6", color = IosActiveBlue, fontSize = (9 * scaleFactor).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(ip, color = IosSecondaryLabel, fontSize = (12 * scaleFactor).sp)
            }
            if (enabled) {
                Icon(Icons.Default.CheckCircle, null, tint = IosActiveGreen, modifier = Modifier.size((20 * scaleFactor).dp))
            }
        }
    }
}

@Composable
private fun DnsResultRow(result: DnsProbeResult, onCopy: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val statusColor = if (result.status == ProbeStatus.SUCCESS) IosActiveGreen else IosErrorRed
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = IosCardBg)) {
        Row(modifier = Modifier.fillMaxWidth().padding((14 * scaleFactor).dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size((10 * scaleFactor).dp).clip(CircleShape).background(statusColor))
            Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (14 * scaleFactor).sp)
                    if (result.isIpv6) {
                        Surface(shape = RoundedCornerShape(6.dp), color = IosActiveBlue.copy(alpha = 0.2f)) {
                            Text("IPv6", color = IosActiveBlue, fontSize = (9 * scaleFactor).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(result.ip, color = IosSecondaryLabel, fontSize = (12 * scaleFactor).sp)
                if (result.status == ProbeStatus.SUCCESS) {
                    Text(
                        strings.DNS_MS.format(result.medianMs) + " · " + result.successCount + "/" + result.totalCount,
                        color = IosActiveGreen,
                        fontSize = (11 * scaleFactor).sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(strings.AUTODETECT_CONNECTION_FAILED, color = IosErrorRed, fontSize = (11 * scaleFactor).sp)
                }
            }
            if (result.status == ProbeStatus.SUCCESS) {
                Text(strings.DNS_MS.format(result.medianMs), color = statusColor, fontWeight = FontWeight.ExtraBold, fontSize = (14 * scaleFactor).sp)
            } else {
                Icon(Icons.Default.Error, null, tint = IosErrorRed, modifier = Modifier.size((20 * scaleFactor).dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onCopy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IosActiveBlue),
                border = BorderStroke(1.dp, IosActiveBlue.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(strings.LOGS_COPY, fontWeight = FontWeight.Bold, fontSize = (11 * scaleFactor).sp)
            }
        }
    }
}

private fun buildResultForProtocol(protocol: AetherProtocol, base: AutoDetectResult): AutoDetectResult {
    val isDPI = base.networkFingerprint.supportsDPI
    return when (protocol) {
        AetherProtocol.MASQUE -> base.copy(
            recommendedProtocol = protocol,
            recommendedNoise = if (isDPI) AetherNoise.GFW else AetherNoise.FIREWALL,
            recommendedScanMode = if (isDPI) AetherScanMode.IRONCLAD else AetherScanMode.BALANCED,
            recommendedH2Mode = true,
            recommendedEch = isDPI,
            recommendedFragment = isDPI,
            recommendedNoDataCheck = false
        )
        AetherProtocol.WG -> base.copy(
            recommendedProtocol = protocol,
            recommendedNoise = AetherNoise.BALANCED,
            recommendedScanMode = AetherScanMode.TURBO,
            recommendedH2Mode = false,
            recommendedEch = false,
            recommendedFragment = false,
            recommendedNoDataCheck = true
        )
        AetherProtocol.GOOL -> base.copy(
            recommendedProtocol = protocol,
            recommendedNoise = AetherNoise.BALANCED,
            recommendedScanMode = if (isDPI) AetherScanMode.IRONCLAD else AetherScanMode.BALANCED,
            recommendedH2Mode = false,
            recommendedEch = false,
            recommendedFragment = false,
            recommendedNoDataCheck = true
        )
        AetherProtocol.ZERO_TRUST -> base.copy(
            recommendedProtocol = protocol,
            recommendedNoise = AetherNoise.OFF,
            recommendedScanMode = AetherScanMode.BALANCED,
            recommendedH2Mode = false,
            recommendedEch = false,
            recommendedFragment = false,
            recommendedNoDataCheck = true
        )
    }
}

@Composable
private fun qualityLabel(latencyMs: Long): Pair<String, Color> {
    val strings = LocalAppStrings.current
    return when {
        latencyMs < 80 -> strings.AUTODETECT_QUALITY_EXCELLENT to IosActiveGreen
        latencyMs < 180 -> strings.AUTODETECT_QUALITY_GOOD to IosActiveBlue
        latencyMs < 350 -> strings.AUTODETECT_QUALITY_FAIR to IosAmber
        else -> strings.AUTODETECT_QUALITY_SLOW to IosErrorRed
    }
}

@Composable
private fun ProtocolResultRankRow(
    rank: Int,
    probe: ProtocolProbeResult,
    onApply: () -> Unit,
    scaleFactor: Float
) {
    val strings = LocalAppStrings.current
    val (qualityLabel, qualityColor) = qualityLabel(probe.latencyMs)
    val isBest = rank == 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBg),
        border = if (isBest) BorderStroke(1.5.dp, IosActiveGreen.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (14 * scaleFactor).dp, vertical = (14 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size((34 * scaleFactor).dp)
                    .clip(CircleShape)
                    .background(if (isBest) IosActiveGreen.copy(alpha = 0.18f) else IosGroupBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#$rank",
                    color = if (isBest) IosActiveGreen else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (13 * scaleFactor).sp
                )
            }
            Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    probe.protocol.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15 * scaleFactor).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    strings.AUTODETECT_MEDIAN_RTT.format(probe.latencyMs),
                    color = IosSecondaryLabel,
                    fontSize = (11 * scaleFactor).sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    qualityLabel,
                    color = qualityColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (10 * scaleFactor).sp,
                    letterSpacing = 0.5.sp
                )
                if (isBest) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        strings.AUTODETECT_RECOMMENDED,
                        color = IosActiveGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (9 * scaleFactor).sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = IosSecondaryLabel,
                modifier = Modifier.size((20 * scaleFactor).dp)
            )
        }
    }
}

private fun localizedAutoDetectStep(step: String, strings: io.github.immaghzbad.aetherst.shared.i18n.AppStrings): String {
    // Exact matches (blue card live steps)
    when (step) {
        "Checking internet connection..." -> return strings.AD_STEP_CHECKING_INTERNET
        "No internet connection. Connect to a working network and try again." -> return strings.AD_STEP_NO_INTERNET
        "Checking IPv6 connectivity..." -> return strings.AD_STEP_CHECKING_IPV6
        "Checking DPI restrictions..." -> return strings.AD_STEP_CHECKING_DPI
        "Detecting ISP..." -> return strings.AD_STEP_DETECTING_ISP
        "Network fingerprint complete" -> return strings.AD_STEP_FINGERPRINT_COMPLETE
        "Measuring protocol latency..." -> return strings.AD_STEP_MEASURING_PROTOCOL_LATENCY
        "Discovering optimal MTU..." -> return strings.AD_STEP_DISCOVERING_MTU
        "Testing obfuscation modes..." -> return strings.AD_STEP_TESTING_OBFUSCATION
        "Evaluating scan strategies..." -> return strings.AD_STEP_EVALUATING_SCAN
        "Computing optimal configuration..." -> return strings.AD_STEP_COMPUTING_OPTIMAL
        "Optimal configuration found!" -> return strings.AD_STEP_OPTIMAL_FOUND
        "Detection failed" -> return strings.AD_STEP_DETECTION_FAILED_GENERIC
        "MASQUE: TCP latency..." -> return strings.AD_STEP_MASQUE_TCP
        "MASQUE: HTTPS probe..." -> return strings.AD_STEP_MASQUE_HTTPS_PROBE
        "MASQUE: HTTPS latency..." -> return strings.AD_STEP_MASQUE_HTTPS_LATENCY
        "WireGuard: TCP latency..." -> return strings.AD_STEP_WG_TCP
        "WireGuard: HTTPS probe..." -> return strings.AD_STEP_WG_HTTPS_PROBE
        "Gool: TCP latency..." -> return strings.AD_STEP_GOOL_TCP
        "Gool: HTTPS probe..." -> return strings.AD_STEP_GOOL_HTTPS_PROBE
    }
    // Dynamic patterns with regex
    Regex("""Checking IPv6\.\.\. attempt (\d+)/(\d+)""").matchEntire(step)?.let {
        return strings.AD_STEP_CHECKING_IPV6_ATTEMPT.format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    Regex("""Probing DPI\.\.\. attempt (\d+)/(\d+)""").matchEntire(step)?.let {
        return strings.AD_STEP_PROBING_DPI_ATTEMPT.format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    Regex("""Detecting ISP\.\.\. attempt (\d+)/(\d+)""").matchEntire(step)?.let {
        return strings.AD_STEP_DETECTING_ISP_ATTEMPT.format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    Regex("""Measuring (.+) latency\.\.\.""").matchEntire(step)?.let {
        return strings.AD_STEP_MEASURING_X_LATENCY.format(it.groupValues[1])
    }
    Regex("""Probing MTU (\d+) bytes\.\.\. best (\d+)""").matchEntire(step)?.let {
        return strings.AD_STEP_PROBING_MTU.format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    Regex("""Testing (.+) obfuscation (\d+)/(\d+)\.\.\.""").matchEntire(step)?.let {
        return strings.AD_STEP_TESTING_NOISE.format(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt())
    }
    Regex("""Testing (.+) scan (\d+)/(\d+)\.\.\.""").matchEntire(step)?.let {
        return strings.AD_STEP_TESTING_SCAN.format(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt())
    }
    // Fallback: return original (for logs or unknown steps, keep English)
    return step
}
