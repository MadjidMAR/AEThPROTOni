package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.platform.PlatformContext

interface BinaryManager {
    fun prepareBinary(name: String = "aether"): String
}

expect fun getBinaryManager(context: PlatformContext): BinaryManager
