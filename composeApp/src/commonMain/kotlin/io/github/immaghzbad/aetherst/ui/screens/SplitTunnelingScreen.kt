package io.github.immaghzbad.aetherst.shared.ui.screens
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.immaghzbad.aetherst.platform.AppIcon
import io.github.immaghzbad.aetherst.platform.isDesktop
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings
import io.github.immaghzbad.aetherst.shared.model.AppInfo

private val IosCardBg = AppPalette.surfaceRaised
private val IosSecondaryLabel = AppPalette.textSecondary
private val IosActiveBlue = AppPalette.accent
private val IosActiveGreen = AppPalette.statusConnected
private val IosInactiveTrack = AppPalette.inactiveTrack

@Composable
fun SplitTunnelingScreen(
    apps: List<AppInfo>,
    excludedPackages: Set<String>,
    blockedPackages: Set<String>,
    tunnelAllApps: Boolean,
    onUpdateMode: (String, Int) -> Unit,
    onBack: () -> Unit,
    scaleFactor: Float = 1f,
    tunneledPackages: Set<String> = emptySet(),
    onBulkUpdateMode: (List<String>, Int) -> Unit = { _, _ -> }
) {
    val strings = LocalAppStrings.current
    val effectiveTunneled = if (tunneledPackages.isNotEmpty() || excludedPackages.isEmpty()) tunneledPackages else emptySet()
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var animateDialog by remember { mutableStateOf(false) }
    var modeFilter by remember { mutableIntStateOf(0) }
    val isWindowsDesktop = isDesktop

    if (showHelpDialog) {
        SplitTunnelHelpDialog(visible = animateDialog, onDismiss = { animateDialog = false }, onTransitionEnd = { showHelpDialog = false }, scaleFactor = scaleFactor)
    }
    LaunchedEffect(showHelpDialog) { if (showHelpDialog) animateDialog = true }

    val tunneledCount = effectiveTunneled.size
    val blockedCount = blockedPackages.size
    val bypassCount = if (tunnelAllApps) 0 else (apps.size - tunneledCount - blockedCount).coerceAtLeast(0)

    val filteredApps = remember(apps, searchQuery, selectedTab, effectiveTunneled, blockedPackages, modeFilter) {
        apps.filter { app ->
            val matchesTab = if (selectedTab == 0) !app.isSystemApp else app.isSystemApp
            val matchesSearch = searchQuery.isEmpty() || app.name.contains(searchQuery, ignoreCase = true) || app.packageName.contains(searchQuery, ignoreCase = true)
            val mode = when {
                blockedPackages.contains(app.packageName) -> 2
                effectiveTunneled.contains(app.packageName) -> 1
                else -> 0
            }
            val matchesMode = when (modeFilter) {
                1 -> mode == 1
                2 -> mode == 0
                3 -> mode == 2
                else -> true
            }
            matchesTab && matchesSearch && matchesMode
        }.sortedWith(compareBy<AppInfo> {
            when {
                blockedPackages.contains(it.packageName) -> 0
                effectiveTunneled.contains(it.packageName) -> 1
                else -> 2
            }
        }.thenBy { it.name.lowercase() })
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus() }) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = (8 * scaleFactor).dp, end = (16 * scaleFactor).dp, top = if (isWindowsDesktop) 12.dp else 36.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size((36 * scaleFactor).dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size((20 * scaleFactor).dp)) }
            Spacer(modifier = Modifier.width((4 * scaleFactor).dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.SPLIT_TITLE, fontWeight = FontWeight.Bold, color = Color.White, fontSize = (18 * scaleFactor).sp, lineHeight = (20 * scaleFactor).sp)
                Text(if (tunnelAllApps) strings.SPLIT_WHOLE_DEVICE_TUNNELED else strings.SPLIT_STATS_TUNNEL_BYPASS_BLOCKED.format(tunneledCount, bypassCount, blockedCount), color = IosSecondaryLabel, fontSize = (11 * scaleFactor).sp)
            }
            IconButton(onClick = { showHelpDialog = true }, modifier = Modifier.size((36 * scaleFactor).dp)) { Icon(Icons.Default.Info, null, tint = IosActiveBlue, modifier = Modifier.size((20 * scaleFactor).dp)) }
        }

        if (tunnelAllApps) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scaleFactor).dp, vertical = (6 * scaleFactor).dp).background(Color(0xFFFFD60A).copy(alpha = 0.1f), RoundedCornerShape(10.dp)).padding((10 * scaleFactor).dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = Color(0xFFFFD60A), modifier = Modifier.size((16 * scaleFactor).dp))
                Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                Column {
                    Text(strings.SPLIT_WHOLE_DEVICE_ON, color = Color(0xFFFFD60A), fontWeight = FontWeight.Bold, fontSize = (12 * scaleFactor).sp, textAlign = TextAlign.Start)
                    Text(strings.SPLIT_WHOLE_DEVICE_ON_DESC, color = Color.White.copy(alpha = 0.6f), fontSize = (11 * scaleFactor).sp, lineHeight = (15 * scaleFactor).sp, textAlign = TextAlign.Start)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scaleFactor).dp, vertical = (4 * scaleFactor).dp).background(IosActiveGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding((8 * scaleFactor).dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, null, tint = IosActiveGreen, modifier = Modifier.size((14 * scaleFactor).dp))
                Spacer(modifier = Modifier.width((6 * scaleFactor).dp))
                Text(strings.SPLIT_DEFAULT_HINT, color = IosSecondaryLabel, fontSize = (11 * scaleFactor).sp, lineHeight = (14 * scaleFactor).sp, textAlign = TextAlign.Start)
            }
        }

        Box(modifier = Modifier.padding(horizontal = (16 * scaleFactor).dp, vertical = (6 * scaleFactor).dp)) {
            BasicTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().height((40 * scaleFactor).dp).background(IosCardBg, RoundedCornerShape(10.dp)), textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = (13 * scaleFactor).sp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }), singleLine = true, cursorBrush = SolidColor(IosActiveBlue), decorationBox = { innerTextField ->
                Row(modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = IosSecondaryLabel, modifier = Modifier.size((18 * scaleFactor).dp))
                    Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) Text(if (isWindowsDesktop) strings.SPLIT_SEARCH_HINT_DESKTOP else strings.SPLIT_SEARCH_HINT, color = IosSecondaryLabel, fontSize = (12 * scaleFactor).sp)
                        innerTextField()
                    }
                    if (searchQuery.isNotEmpty()) Icon(Icons.Default.Block, null, tint = IosSecondaryLabel, modifier = Modifier.size((16 * scaleFactor).dp).clickable { searchQuery = "" })
                }
            })
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scaleFactor).dp, vertical = (4 * scaleFactor).dp).background(IosCardBg, RoundedCornerShape(10.dp)).padding(2.dp)) {
            listOf(strings.SPLIT_TAB_USER_APPS, strings.SPLIT_TAB_SYSTEM_APPS).forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSelected) IosActiveBlue else Color.Transparent).clickable { selectedTab = index }.padding(vertical = (7 * scaleFactor).dp), contentAlignment = Alignment.Center) {
                    Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else IosSecondaryLabel, fontSize = (11 * scaleFactor).sp)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scaleFactor).dp, vertical = (4 * scaleFactor).dp), horizontalArrangement = Arrangement.spacedBy((6 * scaleFactor).dp)) {
            listOf(strings.SPLIT_FILTER_ALL, strings.SPLIT_FILTER_TUNNEL, strings.SPLIT_FILTER_BYPASS, strings.SPLIT_FILTER_BLOCKED).forEachIndexed { index, label ->
                val isSelected = modeFilter == index
                val count = when (index) { 1 -> tunneledCount; 2 -> bypassCount; 3 -> blockedCount; else -> filteredApps.size }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSelected) Color.White.copy(alpha = 0.12f) else IosCardBg).clickable { modeFilter = index }.padding(vertical = (6 * scaleFactor).dp), contentAlignment = Alignment.Center) {
                    Text("$label • $count", color = if (isSelected) Color.White else IosSecondaryLabel, fontSize = (10 * scaleFactor).sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }

        if (!tunnelAllApps) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scaleFactor).dp, vertical = (2 * scaleFactor).dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val pkgs = filteredApps.filter { effectiveTunneled.contains(it.packageName) || blockedPackages.contains(it.packageName) }.map { it.packageName }
                    if (pkgs.isNotEmpty()) onBulkUpdateMode(pkgs, 0)
                }, contentPadding = PaddingValues(horizontal = (8 * scaleFactor).dp, vertical = (2 * scaleFactor).dp)) { Text(strings.SPLIT_BYPASS_ALL, color = IosSecondaryLabel, fontSize = (11 * scaleFactor).sp) }
                TextButton(onClick = {
                    val pkgs = filteredApps.filter { !effectiveTunneled.contains(it.packageName) }.map { it.packageName }
                    if (pkgs.isNotEmpty()) onBulkUpdateMode(pkgs, 1)
                }, contentPadding = PaddingValues(horizontal = (8 * scaleFactor).dp, vertical = (2 * scaleFactor).dp)) { Text(strings.SPLIT_TUNNEL_ALL, color = IosActiveBlue, fontSize = (11 * scaleFactor).sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = {
                    val pkgs = filteredApps.filter { !blockedPackages.contains(it.packageName) }.map { it.packageName }
                    if (pkgs.isNotEmpty()) onBulkUpdateMode(pkgs, 2)
                }, contentPadding = PaddingValues(horizontal = (8 * scaleFactor).dp, vertical = (2 * scaleFactor).dp)) { Text(strings.SPLIT_BLOCK_ALL, color = AppPalette.statusError, fontSize = (11 * scaleFactor).sp, fontWeight = FontWeight.Bold) }
            }
        }

        if (isWindowsDesktop) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scaleFactor).dp, vertical = (2 * scaleFactor).dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.SPLIT_APPS_TUNNELED.format(filteredApps.size, tunneledCount), color = IosSecondaryLabel, fontSize = (11 * scaleFactor).sp, fontWeight = FontWeight.Medium)
                if (searchQuery.isNotEmpty()) Text(strings.SPLIT_FOR_QUERY.format(searchQuery), color = IosActiveBlue, fontSize = (11 * scaleFactor).sp, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = (16 * scaleFactor).dp, end = (16 * scaleFactor).dp, bottom = (24 * scaleFactor).dp), verticalArrangement = Arrangement.spacedBy((8 * scaleFactor).dp)) {
            items(filteredApps, key = { it.packageName }) { app ->
                val mode = when {
                    blockedPackages.contains(app.packageName) -> 2
                    effectiveTunneled.contains(app.packageName) -> 1
                    else -> 0
                }
                AppLineItem(app = app, mode = mode, onUpdateMode = { m -> onUpdateMode(app.packageName, m) }, enabled = !tunnelAllApps, scaleFactor = scaleFactor)
            }
        }
    }
}

@Composable
private fun AppLineItem(app: AppInfo, mode: Int, onUpdateMode: (Int) -> Unit, enabled: Boolean = true, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val bg = when (mode) {
        2 -> AppPalette.statusError.copy(alpha = 0.08f)
        1 -> IosActiveBlue.copy(alpha = 0.08f)
        else -> IosCardBg
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape((12 * scaleFactor).dp)).padding(horizontal = (10 * scaleFactor).dp, vertical = (8 * scaleFactor).dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app = app, modifier = Modifier.size((36 * scaleFactor).dp).clip(RoundedCornerShape((8 * scaleFactor).dp)))
        Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, fontWeight = FontWeight.SemiBold, color = if (mode == 2) AppPalette.statusError else Color.White, fontSize = (13 * scaleFactor).sp, maxLines = 1, textAlign = TextAlign.Left)
            Text(app.packageName, color = IosSecondaryLabel, fontSize = (10 * scaleFactor).sp, maxLines = 1, textAlign = TextAlign.Left)
            if (mode != 0) Text(if (mode == 1) strings.SPLIT_TUNNEL_VPN else strings.SPLIT_BLOCKED_NO_INTERNET, color = if (mode == 1) IosActiveBlue else AppPalette.statusError, fontSize = (10 * scaleFactor).sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Left)
        }
        Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
        IconButton(onClick = { if (!enabled) return@IconButton; onUpdateMode(if (mode == 2) 0 else 2) }, modifier = Modifier.size((32 * scaleFactor).dp), enabled = enabled) {
            Box(modifier = Modifier.size((26 * scaleFactor).dp).background(if (mode == 2) AppPalette.statusError else Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Block, null, tint = if (mode == 2) Color.White else IosSecondaryLabel, modifier = Modifier.size((16 * scaleFactor).dp))
            }
        }
        Spacer(modifier = Modifier.width((4 * scaleFactor).dp))
        Switch(checked = mode == 1, onCheckedChange = { checked -> if (!enabled || mode == 2) return@Switch; onUpdateMode(if (checked) 1 else 0) }, enabled = enabled && mode != 2, modifier = Modifier.graphicsLayer { scaleX = 0.75f; scaleY = 0.75f }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IosActiveBlue, checkedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White, uncheckedTrackColor = IosInactiveTrack, uncheckedBorderColor = Color.Transparent, disabledCheckedTrackColor = IosActiveBlue.copy(alpha = 0.4f), disabledUncheckedTrackColor = IosInactiveTrack.copy(alpha = 0.4f)))
    }
    }
}

@Composable
private fun SplitTunnelHelpDialog(visible: Boolean, onDismiss: () -> Unit, onTransitionEnd: () -> Unit, scaleFactor: Float) {
    val strings = LocalAppStrings.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f), exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.9f)) {
            DisposableEffect(Unit) { onDispose { onTransitionEnd() } }
            Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(modifier = Modifier.fillMaxWidth().padding((12 * scaleFactor).dp).clip(RoundedCornerShape((20 * scaleFactor).dp)).background(AppPalette.surfaceRaised.copy(alpha = 0.98f)).clickable(enabled = false) {}.padding((20 * scaleFactor).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Info, null, tint = IosActiveBlue, modifier = Modifier.size((20 * scaleFactor).dp))
                        Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                        Text(strings.SPLIT_HELP_TITLE, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (16 * scaleFactor).sp, textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
                    Column(verticalArrangement = Arrangement.spacedBy((14 * scaleFactor).dp)) {
                        HelpItem(title = strings.SPLIT_HELP_BYPASS_TITLE, desc = strings.SPLIT_HELP_BYPASS_DESC, icon = Icons.Default.Public, color = IosActiveGreen, scaleFactor = scaleFactor)
                        HelpItem(title = strings.SPLIT_HELP_TUNNEL_TITLE, desc = strings.SPLIT_HELP_TUNNEL_DESC, icon = Icons.Default.Security, color = IosActiveBlue, scaleFactor = scaleFactor)
                        HelpItem(title = strings.SPLIT_HELP_BLOCKED_TITLE, desc = strings.SPLIT_HELP_BLOCKED_DESC, icon = Icons.Default.Block, color = AppPalette.statusError, scaleFactor = scaleFactor)
                    }
                    Spacer(modifier = Modifier.height((16 * scaleFactor).dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height((44 * scaleFactor).dp), colors = ButtonDefaults.buttonColors(containerColor = IosActiveBlue, contentColor = Color.White), shape = RoundedCornerShape(12.dp)) { Text(strings.ROUTING_HELP_GOT_IT, fontWeight = FontWeight.Bold, fontSize = (14 * scaleFactor).sp) }
                }
                }
            }
        }
    }
}

@Composable
private fun HelpItem(title: String, desc: String, icon: ImageVector, color: Color, scaleFactor: Float) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size((36 * scaleFactor).dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size((18 * scaleFactor).dp)) }
        Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = (13 * scaleFactor).sp, textAlign = TextAlign.Start)
            Spacer(modifier = Modifier.height((2 * scaleFactor).dp))
            Text(desc, color = Color.White.copy(alpha = 0.6f), fontSize = (11 * scaleFactor).sp, lineHeight = (15 * scaleFactor).sp, textAlign = TextAlign.Start)
        }
    }
}
