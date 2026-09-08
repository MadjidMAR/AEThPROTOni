package io.github.immaghzbad.aetherst.shared.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry

@Composable
expect fun LogsVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
)

@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
)

expect fun ClipEntry.textOrNull(): String?
