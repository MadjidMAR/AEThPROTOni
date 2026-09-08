package io.github.immaghzbad.aetherst.shared.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry

@Composable
actual fun LogsVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier
) {
    
}

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    BackHandler(enabled = enabled, onBack = onBack)
}

actual fun ClipEntry.textOrNull(): String? = runCatching {
    clipData.getItemAt(0).text?.toString()
}.getOrNull()
