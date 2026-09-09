package dev.lumenchess.arena

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.lumenchess.MainActivity
import dev.lumenchess.core.chess.*
import dev.lumenchess.data.persistence.*
import dev.lumenchess.engine.api.EngineSearchInfo
import dev.lumenchess.engine.api.UciScore
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArenaBranchIntegrationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[ArenaViewModel::class.java]
    private val state get() = requireNotNull(vm.uiState.value.runtime)

    @Test fun resumeCancelsPendingBranchCapture() {
        startManualHistory()
        val original = vm.currentCoordinatorForTest()
        afterPersistenceCallbacks { vm.branchHere(); vm.resume() }
        assertEquals(ArenaScreenMode.LIVE, vm.uiState.value.mode)
        assertSame(original, vm.currentCoordinatorForTest())
        assertNull(vm.uiState.value.branchDraft)
        assertFalse(vm.uiState.value.branchOperationPending)
        assertFalse(state.paused)
        assertNull(vm.uiState.value.historyPly)
    }

    @Test fun historyStepCancelsPendingBranchCapture() {
        startManualHistory()
        afterPersistenceCallbacks { vm.branchHere(); vm.stepHistory(-1) }
        assertEquals(ArenaScreenMode.LIVE, vm.uiState.value.mode)
        assertEquals(1, vm.uiState.value.historyPly)
        assertNull(vm.uiState.value.branchDraft)
        assertFalse(vm.uiState.value.branchOperationPending)
        assertEquals(2, state.gameTree.mainline().size)
    }

    @Test fun closeHistoryCancelsPendingBranchCapture() {
        startManualHistory()
        afterPersistenceCallbacks { vm.branchHere(); vm.closeHistory() }
        assertEquals(ArenaScreenMode.LIVE, vm.uiState.value.mode)
        assertNull(vm.uiState.value.historyPly)
        assertNull(vm.uiState.value.branchDraft)
        assertFalse(vm.uiState.value.branchOperationPending)
        assertTrue(state.paused)
    }

    @Test fun leavingScreenCancelsPendingBranchCapture() {
        startManualHistory()
        afterPersistenceCallbacks { vm.branchHere(); vm.onScreenStopped() }
        assertEquals(ArenaScreenMode.LIVE, vm.uiState.value.mode)
        assertNull(vm.uiState.value.branchDraft)
        assertFalse(vm.uiState.value.branchOperationPending)
        assertTrue(state.paused)
        rule.runOnUiThread { vm.onScreenStarted() }
    }

    @Test fun newerBranchCaptureUsesNewHistorySelection() {
        startManualHistory()
        val expected = Fen.serialize(state.gameTree.mainline().first().position)
        afterPersistenceCallbacks { vm.branchHere(); vm.stepHistory(-1); vm.branchHere() }
        assertEquals(ArenaScreenMode.SETUP, vm.uiState.value.mode)
        assertEquals(expected, requireNotNull(vm.uiState.value.branchDraft).fen)
        assertFalse(vm.uiState.value.branchOperationPending)
    }

    @Test fun historicalPositionDoesNotDisplayLiveEvaluation() {
        startManualHistory()
        rule.runOnUiThread {
            vm.resume()
            val coordinator = requireNotNull(vm.currentCoordinatorForTest())
            coordinator.onEngineHostRecovered(Color.WHITE)
            coordinator.onEngineHostRecovered(Color.BLACK)
            vm.returnToEngine()
            val search = requireNotNull(state.pendingEngineSearch)
            coordinator.onEngineInfo(Color.WHITE, EngineSearchInfo(
                search.searchId, search.positionRevision, depth = 19, score = UciScore.Centipawns(821),
            ))
            vm.takeOver(ArenaManualSide.BOTH)
        }
        rule.onNodeWithText("+8.21").assertExists()
        rule.runOnUiThread { vm.browseHistory(); vm.stepHistory(-1) }
        rule.onNodeWithText("+8.21").assertDoesNotExist()
        rule.onNodeWithText("d19").assertDoesNotExist()
    }

    @Test fun resumeCancelsPendingOriginalReturn() {
        startSandbox()
        val sandbox = vm.currentCoordinatorForTest()
        afterOriginalCallbacks { vm.resume() }
        assertSame(sandbox, vm.currentCoordinatorForTest())
        assertNotNull(vm.uiState.value.resolvedSetup?.branchOrigin)
        assertFalse(vm.uiState.value.branchOperationPending)
        assertFalse(state.paused)
    }

    @Test fun historyNavigationCancelsPendingOriginalReturn() {
        startSandbox()
        val sandbox = vm.currentCoordinatorForTest()
        afterOriginalCallbacks { vm.browseHistory() }
        assertSame(sandbox, vm.currentCoordinatorForTest())
        assertEquals(0, vm.uiState.value.historyPly)
        assertFalse(vm.uiState.value.branchOperationPending)
        assertTrue(state.paused)
    }

    @Test fun leavingScreenCancelsPendingOriginalReturn() {
        startSandbox()
        val sandbox = vm.currentCoordinatorForTest()
        afterOriginalCallbacks { vm.onScreenStopped() }
        assertSame(sandbox, vm.currentCoordinatorForTest())
        assertNotNull(vm.uiState.value.resolvedSetup?.branchOrigin)
        assertFalse(vm.uiState.value.branchOperationPending)
        assertTrue(state.paused)
        rule.runOnUiThread { vm.onScreenStarted() }
    }

    @Test fun stopInvalidatesPendingOriginalLoad() {
        rule.onNodeWithTag("main-tab-arena").performClick()
        rule.runOnUiThread {
            vm.updateManualSide(ArenaManualSide.BOTH)
            vm.updateManualLimitMode(ArenaManualLimitMode.UNTIL_RELEASE)
            vm.startNewArena()
        }
        move("e2", "e4")
        rule.onNodeWithTag("arena-branch").performClick()
        rule.onNodeWithTag("arena-branch-here").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.branchDraft != null }
        rule.onNodeWithTag("arena-start").performScrollTo().performClick()
        move("c7", "c5")
        rule.runOnUiThread { vm.returnToOriginal(); vm.stopArena() }
        val started = android.os.SystemClock.elapsedRealtime()
        rule.waitUntil(5_000) { android.os.SystemClock.elapsedRealtime() - started > 800 }
        assertEquals(ArenaScreenMode.SETUP, vm.uiState.value.mode)
        assertEquals(null, vm.uiState.value.runtime)
        assertFalse(vm.uiState.value.branchOperationPending)
    }

    @Test fun historicalSandboxIsSeparateUntilExplicitSaveAndOriginalReturnsPaused() {
        rule.onNodeWithTag("main-tab-arena").performClick()
        rule.runOnUiThread {
            vm.updateManualSide(ArenaManualSide.BOTH)
            vm.updateManualLimitMode(ArenaManualLimitMode.UNTIL_RELEASE)
            vm.startNewArena()
        }
        move("e2", "e4"); move("e7", "e5")
        val originalFen = Fen.serialize(state.position)
        val bounds = rule.onNodeWithTag("arena-board-stage").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("arena-branch").performClick()
        rule.onNodeWithTag("arena-history-prev").performClick()
        assertEquals(1, vm.uiState.value.historyPly)
        assertEquals(originalFen, Fen.serialize(state.position))
        rule.onNodeWithTag("arena-branch-here").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.branchDraft != null }
        val origin = requireNotNull(vm.uiState.value.branchDraft)
        rule.onNodeWithText("Without clocks").performScrollTo().performClick()
        rule.onNodeWithTag("arena-start").performScrollTo().performClick()
        assertFalse(state.clock.enabled)
        move("c7", "c5")
        rule.waitUntil(10_000) { vm.uiState.value.gameId != null }
        assertNotEquals(origin.gameId.value, vm.uiState.value.gameId)
        assertEquals(bounds, rule.onNodeWithTag("arena-board-stage").fetchSemanticsNode().boundsInRoot)
        val db = LumenDatabaseFactory.open(rule.activity)
        try {
            val repository = GamePersistenceRepository(db)
            val before = runBlocking { repository.loadGame(origin.gameId) }!!
            assertEquals(2, before.tree.mainline().size)
            assertEquals(3, before.tree.nodes.size)
            rule.onNodeWithTag("arena-save-variation").performClick()
            rule.waitUntil(10_000) { vm.uiState.value.message?.startsWith("Saved") == true }
            val after = runBlocking { repository.loadGame(origin.gameId) }!!
            assertEquals(originalFen, Fen.serialize(after.tree.mainline().last().position))
            assertEquals(4, after.tree.nodes.size)
            rule.onNodeWithTag("arena-original").performClick()
            rule.waitUntil(10_000) { vm.uiState.value.gameId == origin.gameId.value && vm.uiState.value.mode == ArenaScreenMode.LIVE }
            assertEquals(originalFen, Fen.serialize(state.position))
            assertTrue(state.paused)
            assertEquals(bounds, rule.onNodeWithTag("arena-board-stage").fetchSemanticsNode().boundsInRoot)
            rule.onNodeWithTag("arena-stop").performClick()
            assertFalse(vm.uiState.value.setup.untimed)
            assertEquals(ArenaOpeningMode.NORMAL, vm.uiState.value.setup.opening.mode)
        } finally { LumenDatabaseFactory.close(db) }
    }

    private fun startManualHistory() {
        rule.onNodeWithTag("main-tab-arena").performClick()
        rule.runOnUiThread {
            vm.updateManualSide(ArenaManualSide.BOTH)
            vm.updateManualLimitMode(ArenaManualLimitMode.UNTIL_RELEASE)
            vm.startNewArena()
        }
        move("e2", "e4"); move("e7", "e5")
        rule.runOnUiThread { vm.browseHistory() }
    }

    private fun startSandbox() {
        startManualHistory()
        afterPersistenceCallbacks { vm.branchHere() }
        rule.runOnUiThread { vm.startNewArena() }
        assertNotNull(vm.uiState.value.resolvedSetup?.branchOrigin)
    }

    private fun afterOriginalCallbacks(action: () -> Unit) {
        val delivered = CountDownLatch(1)
        rule.runOnUiThread {
            vm.returnToOriginal()
            val gatewayField = ArenaViewModel::class.java.getDeclaredField("restoreProbe").apply { isAccessible = true }
            val gateway = requireNotNull(gatewayField.get(vm))
            val executorField = AndroidArenaPersistenceGateway::class.java.getDeclaredField("executor").apply { isAccessible = true }
            val executor = executorField.get(gateway) as ExecutorService
            // Queue before cancellation closes the probe; delivery still follows the load callback.
            executor.execute {
                android.os.Handler(android.os.Looper.getMainLooper()).post { delivered.countDown() }
            }
            action()
        }
        assertTrue("Original load callbacks did not drain", delivered.await(10, TimeUnit.SECONDS))
        rule.waitForIdle()
    }

    /** Drain the existing queue and main-loop callbacks without sleeping or changing production APIs. */
    private fun afterPersistenceCallbacks(action: () -> Unit) {
        val delivered = CountDownLatch(1)
        rule.runOnUiThread {
            val gatewayField = ArenaViewModel::class.java.getDeclaredField("persistenceGateway").apply { isAccessible = true }
            val gateway = requireNotNull(gatewayField.get(vm))
            val executorField = AndroidArenaPersistenceGateway::class.java.getDeclaredField("executor").apply { isAccessible = true }
            val executor = executorField.get(gateway) as ExecutorService
            action()
            executor.execute {
                android.os.Handler(android.os.Looper.getMainLooper()).post { delivered.countDown() }
            }
        }
        assertTrue("Persistence callbacks did not drain", delivered.await(10, TimeUnit.SECONDS))
        rule.waitForIdle()
    }

    private fun move(from: String, to: String) {
        val revision = state.positionRevision
        rule.onNodeWithTag("square-$from").performClick()
        rule.onNodeWithTag("square-$to").performClick()
        rule.waitUntil(5_000) { state.positionRevision != revision }
    }
}
