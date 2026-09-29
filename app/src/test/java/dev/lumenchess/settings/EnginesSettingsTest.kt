package dev.lumenchess.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnginesSettingsTest {
    private fun InstalledEngineInfo.detail(label: String) = details.first { it.first == label }.second

    @Test
    fun bothBundledEnginesAreListedWithTheirRealIdentity() {
        val engines = installedEngines()

        assertEquals(listOf("Stockfish 18", "Reckless 0.9.0"), engines.map { it.name })
        assertEquals("GPL-3.0-or-later", engines[0].license)
        assertEquals("AGPL-3.0", engines[1].license)
        assertEquals("18", engines[0].detail("Version"))
        assertTrue(engines[0].detail("Source").startsWith("sf_18 · cb3d4ee9b"))
        assertTrue(engines[1].detail("Source").startsWith("v0.9.0 · 0e92358f5"))
    }

    @Test
    fun strengthDescriptionMatchesWhatEachEngineCanActuallyDo() {
        val (stockfish, reckless) = installedEngines()

        assertTrue(stockfish.detail("Strength").startsWith("Native 1320–3190 Elo"))
        assertEquals("Humanized and Hybrid (no native Elo limit)", reckless.detail("Strength"))
        assertEquals("Standard · Chess960", stockfish.detail("Variants"))
        assertEquals("Standard · Chess960", reckless.detail("Variants"))
    }
}
