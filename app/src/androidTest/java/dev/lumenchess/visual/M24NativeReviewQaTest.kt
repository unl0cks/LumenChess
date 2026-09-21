package dev.lumenchess.visual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.play.PLAY_LIVE_TEST_TAG
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class M24NativeReviewQaTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()

    @Before fun enabledOnlyInM24Lane() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("m24NativeQa") == "true")
    }

    @Test fun customFenSetupValidationAndLiveEvidence() {
        waitFor("p5-play-overview")
        compose.onNodeWithTag("main-tab-play").performClick()
        waitFor("p5-play-overview")
        compose.onNodeWithTag("play-overview-vs-engine").performClick()
        waitFor(PLAY_SETUP_TEST_TAG)
        capture("01-normal-setup")

        val fen = "8/8/8/8/8/4k3/8/4K3 w - - 0 1"
        compose.onNodeWithTag("play-starting-fen").performTextInput("8/8/8")
        compose.waitForIdle()
        capture("02-invalid-fen")
        compose.onNodeWithText("Starting FEN is invalid for standard").assertIsDisplayed()

        compose.onNodeWithTag("play-starting-fen").performTextInput("/8/8/8/8/8/4k3/8/4K3 w - - 0 1")
        compose.waitForIdle()
        capture("03-valid-custom-fen")
        compose.onNodeWithTag("p5-setup-start-button").performClick()
        waitFor(PLAY_LIVE_TEST_TAG, 20_000)
        capture("04-custom-fen-live")
        compose.onNodeWithTag("play-board-stage").assertIsDisplayed()
    }

    private fun waitFor(tag: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "m24-native").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
