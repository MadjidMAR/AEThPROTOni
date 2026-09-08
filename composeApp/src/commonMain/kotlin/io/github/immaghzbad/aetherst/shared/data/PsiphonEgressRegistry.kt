package io.github.immaghzbad.aetherst.shared.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PsiphonEgressRegistry {
    private val _availableRegions = MutableStateFlow<List<String>>(emptyList())
    val availableRegions = _availableRegions.asStateFlow()

    fun setAvailableRegions(regions: List<String>) {
        _availableRegions.value = regions.map { it.trim().uppercase() }.filter { it.matches(Regex("^[A-Z]{2}$")) }.distinct().sorted()
    }

    fun clear() {
        _availableRegions.value = emptyList()
    }
}
