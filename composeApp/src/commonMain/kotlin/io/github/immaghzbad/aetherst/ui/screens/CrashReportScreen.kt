package io.github.immaghzbad.aetherst.shared.ui.screens
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings

private val IosCardBg = AppPalette.surfaceRaised
private val IosGroupBg = AppPalette.divider
private val IosSecondaryLabel = AppPalette.textSecondary
private val IosActiveBlue = AppPalette.accent
private val IosActiveGreen = AppPalette.statusConnected
private val IosErrorRed = AppPalette.statusError

@Composable
fun CrashReportScreen(
    crashLog: String,
    appVersion: String,
    platformName: String,
    deviceModel: String,
    osVersion: String,
    onRestart: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val systemInfo = buildString {
        appendLine("═══════════════════════════════")
        appendLine("  ${strings.CRASH_REPORT_TITLE}")
        appendLine("═══════════════════════════════")
        appendLine()
        appendLine("📱 ${strings.CRASH_DEVICE_INFORMATION}")
        appendLine("───────────────────────────────")
        appendLine("  ${strings.CRASH_DEVICE}:    $deviceModel")
        appendLine("  ${strings.CRASH_PLATFORM}:  $platformName")
        appendLine("  ${strings.CRASH_OS_VERSION}:        $osVersion")
        appendLine("  ${strings.CRASH_APP_BUILD}:       v$appVersion")
        appendLine()
        appendLine("📋 ${strings.CRASH_STACK_TRACE_SHARE}")
        appendLine("───────────────────────────────")
        appendLine(crashLog)
        appendLine()
        appendLine("═══════════════════════════════")
        appendLine("  ${strings.CRASH_GENERATED_BY}")
        appendLine("═══════════════════════════════")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
            .padding(top = if (platformName == "Windows") 12.dp else 36.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = IosErrorRed.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = IosErrorRed,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                strings.CRASH_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                strings.CRASH_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = IosSecondaryLabel,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            strings.CRASH_DIAGNOSTICS,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = IosSecondaryLabel,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = IosCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DiagnosticRow(icon = Icons.Default.PhoneAndroid, label = strings.CRASH_DEVICE, value = deviceModel, onCopy = { onCopy(deviceModel) })
                HorizontalDivider(color = IosGroupBg, modifier = Modifier.padding(vertical = 8.dp))
                DiagnosticRow(icon = Icons.Default.Computer, label = strings.CRASH_PLATFORM, value = platformName, onCopy = { onCopy(platformName) })
                HorizontalDivider(color = IosGroupBg, modifier = Modifier.padding(vertical = 8.dp))
                DiagnosticRow(icon = Icons.Default.Memory, label = strings.CRASH_OS_VERSION, value = osVersion, onCopy = { onCopy(osVersion) })
                HorizontalDivider(color = IosGroupBg, modifier = Modifier.padding(vertical = 8.dp))
                DiagnosticRow(icon = Icons.Default.Tag, label = strings.CRASH_APP_BUILD, value = "v$appVersion", onCopy = { onCopy("v$appVersion") })
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { onShare(systemInfo) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = IosActiveGreen.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Share, null, tint = IosActiveGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.CRASH_SHARE, color = IosActiveGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = {
                    onCopy(systemInfo)
                    onShowToast(strings.TOAST_REPORT_COPIED)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = IosActiveBlue.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = IosActiveBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.CRASH_COPY, color = IosActiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                strings.CRASH_STACK_TRACE,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = IosSecondaryLabel,
                letterSpacing = 1.sp
            )
            Text(
                strings.CRASH_LINES.format(crashLog.lines().size),
                color = IosSecondaryLabel,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = IosCardBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = crashLog,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onRestart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = IosActiveBlue,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(strings.CRASH_CLEAR_RESTART, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun DiagnosticRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = IosActiveBlue.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = IosSecondaryLabel, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (onCopy != null) Modifier.clickable { onCopy() } else Modifier
        ) {
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.ContentCopy, null,
                    tint = IosActiveBlue.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
