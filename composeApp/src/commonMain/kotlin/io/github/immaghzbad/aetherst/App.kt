package io.github.immaghzbad.aetherst.shared

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.shared.i18n.LocalAppStrings
import io.github.immaghzbad.aetherst.shared.i18n.getEffectiveStrings
import io.github.immaghzbad.aetherst.shared.ui.AetherViewModel
import io.github.immaghzbad.aetherst.shared.ui.OnboardingViewModel
import io.github.immaghzbad.aetherst.shared.ui.screens.MainScreen
import io.github.immaghzbad.aetherst.shared.ui.theme.MyApplicationTheme

@Composable
fun App(context: PlatformContext) {
    val viewModel: AetherViewModel = viewModel(key = "aether_main_vm") { AetherViewModel(context) }
    val onboardingViewModel: OnboardingViewModel = viewModel(key = "onboarding_vm") { OnboardingViewModel(context) }
    val config by viewModel.config.collectAsStateWithLifecycle()
    val strings = getEffectiveStrings(config.appLanguage)
    val isRtl = config.resolvedLanguage() == "fa"
    CompositionLocalProvider(LocalAppStrings provides strings) {
        MyApplicationTheme(isRtl = isRtl) {
            MainScreen(viewModel, onboardingViewModel, context)
        }
    }
}
