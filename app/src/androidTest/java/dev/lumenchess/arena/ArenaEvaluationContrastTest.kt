package dev.lumenchess.arena

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.settings.AppAppearance
import dev.lumenchess.settings.AppearanceSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArenaEvaluationContrastTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scoreAndDepthRemainReadableAcrossTheSplitInBothAppearances() {
        var appearance by mutableStateOf(AppAppearance.DARK)
        var evaluation by mutableStateOf(ArenaEvaluation(whiteCentipawns = 0, depth = 11))
        composeRule.setContent {
            LumenTheme(AppearanceSettings(appearance = appearance)) {
                ArenaEvaluationBar(evaluation, Modifier.width(320.dp))
            }
        }

        for (mode in listOf(AppAppearance.DARK, AppAppearance.LIGHT)) {
            for (score in listOf(-400, 0, 36, 400)) {
                composeRule.runOnIdle {
                    appearance = mode
                    evaluation = ArenaEvaluation(whiteCentipawns = score, depth = 11)
                }
                assertTextContrast(evaluation.label())
                assertTextContrast("d11")
            }
        }
    }

    private fun assertTextContrast(text: String) {
        val node = composeRule.onNodeWithText(text)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color.luminance()
        val pixels = node.captureToImage().toPixelMap()
        // Sample the real rendered padding on both sides of the text. Removing the label
        // backing exposes the changing split and fails for at least one side/theme.
        for (x in listOf(1, pixels.width - 2)) {
            val background = pixels[x, pixels.height / 2].luminance()
            val contrast = (maxOf(foreground, background) + .05f) /
                (minOf(foreground, background) + .05f)
            assertTrue("$text at x=$x has insufficient contrast: $contrast", contrast >= 4.5f)
        }
    }
}
