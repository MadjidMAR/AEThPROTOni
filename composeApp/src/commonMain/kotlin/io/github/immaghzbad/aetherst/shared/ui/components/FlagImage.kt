package io.github.immaghzbad.aetherst.shared.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

expect @Composable fun CountryFlag(
    countryCode: String,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
)
