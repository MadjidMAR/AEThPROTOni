package io.github.immaghzbad.aetherst.shared.ui.screens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.immaghzbad.aetherst.shared.ui.theme.AppPalette

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immaghzbad.aetherst.shared.model.LogEntry
import io.github.immaghzbad.aetherst.shared.model.LogLevel
import io.github.immaghzbad.aetherst.shared.model.AetherLogLevel
import io.github.immaghzbad.aetherst.platform.isDesktop
import io.github.immaghzbad.aetherst.shared.ui.AetherViewModel
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings
import io.github.immaghzbad.aetherst.shared.ui.components.LogsVerticalScrollbar
import kotlinx.coroutines.launch

private val IosCardBg = AppPalette.surfaceRaised
private val IosSecondaryLabel = AppPalette.textSecondary
private val IosActiveBlue = AppPalette.accent
private val IosActiveGreen = AppPalette.statusConnected
private val IosWarnAmber = AppPalette.statusScanning
private val IosErrorRed = AppPalette.statusError
private val IosDebugCyan = AppPalette.debugCyan

@Composable
fun LogsScreen(
    viewModel: AetherViewModel,
    onShowToast: (String, Boolean) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = 0.dp
) {
    val strings = LocalAppStrings.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)
        val horizontalPadding = if (screenWidth < 360.dp) 10.dp else 16.dp

        val config by viewModel.config.collectAsStateWithLifecycle()
        val logs by viewModel.logs.collectAsStateWithLifecycle()

        var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }
        var selectedSourceFilter by remember { mutableStateOf("ALL") }
        var searchQuery by remember { mutableStateOf("") }
        var isSearchFocused by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()

        val filteredLogs = remember(logs, selectedLevelFilter, selectedSourceFilter, searchQuery) {
            logs.filter { entry ->
                val isCoreEntry = entry.tag.equals("AetherCore", ignoreCase = true)
                val sourceMatches = when (selectedSourceFilter) {
                    "CORE" -> isCoreEntry
                    "APP" -> !isCoreEntry
                    else -> true
                }
                val levelMatches = selectedLevelFilter == null || entry.level == selectedLevelFilter
                val searchMatches = searchQuery.isEmpty() || entry.message.contains(searchQuery, ignoreCase = true) || entry.tag.contains(searchQuery, ignoreCase = true)
                sourceMatches && levelMatches && searchMatches
            }
        }

        LaunchedEffect(filteredLogs.size) {
            if (filteredLogs.isNotEmpty()) {
                try {
                    listState.scrollToItem(filteredLogs.size - 1)
                } catch (_: Exception) {
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    top = if (isDesktop) 12.dp else 36.dp,
                    end = horizontalPadding,
                    bottom = bottomContentPadding + 10.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.LOGS_TITLE,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = (26 * scaleFactor).sp
                    )
                    Text(
                        text = strings.LOGS_SUBTITLE,
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSecondaryLabel,
                        fontSize = (11 * scaleFactor).sp
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            viewModel.copyLogs()
                            onShowToast(strings.LOGS_COPY, false)
                        },
                        modifier = Modifier.size((40 * scaleFactor).dp).testTag("copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = strings.LOGS_COPY,
                            tint = IosActiveBlue,
                            modifier = Modifier.size((20 * scaleFactor).dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.shareLogs() },
                        modifier = Modifier.size((40 * scaleFactor).dp).testTag("share_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.LOGS_SHARE,
                            tint = IosActiveBlue,
                            modifier = Modifier.size((20 * scaleFactor).dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.size((40 * scaleFactor).dp).testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.LOGS_CLEAR,
                            tint = IosErrorRed,
                            modifier = Modifier.size((20 * scaleFactor).dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

            if (config.coreLogLevel == AetherLogLevel.OFF) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IosCardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding((12 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = IosActiveBlue,
                            modifier = Modifier.size((18 * scaleFactor).dp)
                        )
                        Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
                        Text(
                            text = strings.LOGS_CORE_OFF_WARNING,
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            fontSize = (10 * scaleFactor).sp,
                            lineHeight = (14 * scaleFactor).sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height((10 * scaleFactor).dp))
            }

            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((44 * scaleFactor).dp)
                    .background(IosCardBg, RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSearchFocused) IosActiveBlue else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { isSearchFocused = it.isFocused }
                    .testTag("search_logs_field"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = (14 * scaleFactor).sp),
                singleLine = true,
                cursorBrush = SolidColor(IosActiveBlue),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = IosSecondaryLabel,
                            modifier = Modifier.size((18 * scaleFactor).dp)
                        )
                        Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("${strings.SEARCH}...", color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp)
                            }
                            innerTextField()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosCardBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sourceFilters = listOf("ALL", "APP", "CORE")

                sourceFilters.forEach { label ->
                    val selected = selectedSourceFilter == label
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    selected && label == "CORE" -> AppPalette.accentVariant
                                    selected -> IosActiveBlue
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { selectedSourceFilter = label }
                            .padding(vertical = (8 * scaleFactor).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (label) {
                                "CORE" -> strings.LOGS_FILTER_CORE
                                "APP" -> strings.LOGS_FILTER_APP_ONLY
                                else -> strings.LOGS_FILTER_ALL_SOURCES
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else IosSecondaryLabel,
                            fontSize = (9 * scaleFactor).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((6 * scaleFactor).dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosCardBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val filters = listOf(
                    strings.LOGS_LEVEL_ALL to null,
                    strings.LOGS_LEVEL_INFO to LogLevel.INFO,
                    strings.LOGS_LEVEL_WARN to LogLevel.WARN,
                    strings.LOGS_LEVEL_ERROR to LogLevel.ERROR,
                    strings.LOGS_LEVEL_DEBUG to LogLevel.DEBUG
                )

                filters.forEach { (label, level) ->
                    val selected = selectedLevelFilter == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) IosActiveBlue else Color.Transparent)
                            .clickable { selectedLevelFilter = level }
                            .padding(vertical = (8 * scaleFactor).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else IosSecondaryLabel,
                            fontSize = (9 * scaleFactor).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = IosCardBg
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val msg = if (config.coreLogLevel == AetherLogLevel.OFF && config.appLogLevel == AetherLogLevel.OFF) {
                            strings.LOGS_EMPTY_DISABLED
                        } else {
                            strings.LOGS_EMPTY_NO_RECORDS
                        }
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            fontSize = (11 * scaleFactor).sp
                        )
                    }
                } else {
                    val scope = rememberCoroutineScope()
                    val showScrollToBottom by remember {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            if (totalItems == 0) {
                                false
                            } else {
                                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisibleItemIndex < totalItems - 1
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding((10 * scaleFactor).dp)
                        ) {
                            items(
                                items = filteredLogs.distinctBy { it.id },
                                key = { it.id }
                            ) { entry ->
                                IosLogLineItem(
                                    entry = entry,
                                    onCopy = { viewModel.copyToClipboard(it) },
                                    scaleFactor = scaleFactor
                                )
                            }
                        }

                        LogsVerticalScrollbar(
                            state = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 2.dp, top = 4.dp, bottom = 4.dp)
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollToBottom,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        if (filteredLogs.isNotEmpty()) {
                                            listState.animateScrollToItem(filteredLogs.size - 1)
                                        }
                                    }
                                },
                                containerColor = IosActiveBlue,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Scroll to bottom",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosLogLineItem(
    entry: LogEntry,
    onCopy: (String) -> Unit,
    scaleFactor: Float = 1f
) {
    val levelColor = when (entry.level) {
        LogLevel.INFO -> IosActiveGreen
        LogLevel.WARN -> IosWarnAmber
        LogLevel.ERROR -> IosErrorRed
        LogLevel.DEBUG -> IosDebugCyan
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (4 * scaleFactor).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141416))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    val logText = "[${entry.timestamp}] [${entry.level.name}] [${entry.tag}] ${entry.message}"
                    onCopy(logText)
                }
            )
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width((4 * scaleFactor).dp)
                .fillMaxHeight()
                .background(levelColor)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = (12 * scaleFactor).dp, vertical = (10 * scaleFactor).dp)
                .weight(1f)
        ) {
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF2F2F7),
                fontSize = (12 * scaleFactor).sp,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height((6 * scaleFactor).dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 ${entry.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = IosSecondaryLabel,
                    fontSize = (9 * scaleFactor).sp
                )

                Spacer(modifier = Modifier.width((10 * scaleFactor).dp))

                val isCoreEntry = entry.tag.equals("AetherCore", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isCoreEntry) AppPalette.accentVariant.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (isCoreEntry) "CORE" else "APP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCoreEntry) Color(0xFFB5A8FF) else IosSecondaryLabel,
                        fontSize = (8 * scaleFactor).sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width((6 * scaleFactor).dp))

                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = IosSecondaryLabel.copy(alpha = 0.7f),
                    fontSize = (9 * scaleFactor).sp,
                    textAlign = TextAlign.Start
                )
            }
        }
        }
    }
}
