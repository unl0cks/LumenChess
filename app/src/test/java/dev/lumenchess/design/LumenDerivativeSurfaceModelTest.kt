package dev.lumenchess.design

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LumenDerivativeSurfaceModelTest {
    @Test
    fun selectedFaceIsOpaqueAndNeverUsesFiniteHighlight() {
        val selected = derivativeSurfaceSpec(DerivativeSurfaceRole.SELECTED_FACE)

        assertTrue(selected.requiresOpaqueFill)
        assertFalse(selected.usesFiniteHighlight)
    }

    @Test
    fun disabledSurfaceHasLessDepthThanNeutralRow() {
        val neutral = derivativeSurfaceSpec(DerivativeSurfaceRole.NEUTRAL_ROW)
        val disabled = derivativeSurfaceSpec(DerivativeSurfaceRole.DISABLED_SURFACE)

        assertTrue(disabled.restElevationDp < neutral.restElevationDp)
        assertTrue(disabled.outlineAlpha < neutral.outlineAlpha)
        assertTrue(disabled.illuminationAlpha < neutral.illuminationAlpha)
    }

    @Test
    fun pressedStateChangesDepthWithoutChangingMeasuredGeometry() {
        DerivativeSurfaceRole.entries.forEach { role ->
            val spec = derivativeSurfaceSpec(role)

            assertTrue(spec.pressedElevationDp <= spec.restElevationDp)
            assertTrue(spec.pressedScale in 0.97f..1f)
            assertTrue(spec.pressedOffsetDp >= 0f)
            assertTrue(spec.measuredHeightDeltaDp == 0f)
            assertFalse(spec.usesFiniteHighlight)
        }
    }

    @Test
    fun recessedTrayRemainsBelowRaisedAndSelectedSurfaces() {
        val recessed = derivativeSurfaceSpec(DerivativeSurfaceRole.RECESSED_TRAY)
        val neutral = derivativeSurfaceSpec(DerivativeSurfaceRole.NEUTRAL_ROW)
        val selected = derivativeSurfaceSpec(DerivativeSurfaceRole.SELECTED_FACE)

        assertTrue(recessed.restElevationDp < neutral.restElevationDp)
        assertTrue(recessed.restElevationDp < selected.restElevationDp)
        assertTrue(recessed.edgeDarkeningAlpha > neutral.edgeDarkeningAlpha)
    }
}
