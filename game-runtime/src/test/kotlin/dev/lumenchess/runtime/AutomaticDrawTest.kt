package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameResult
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutomaticDrawTest {
    private class FakeTime(var now: Long = 1_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
        fun advanceBy(millis: Long) { now += millis }
    }

    private class Table(fen: String?, clockMillis: Long = 600_000L) {
        val time = FakeTime()
        private var nextId = 1L
        val runtime = GameRuntime.create(
            initialPosition = fen?.let { Fen.parse(it) } ?: Position.initial(),
            clockConfig = ClockConfig(clockMillis, 0L),
            timeSource = time,
            controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.HUMAN),
        )

        init { dispatch { RuntimeEvent.Start(it) } }

        fun dispatch(build: (RuntimeEventId) -> RuntimeEvent): RuntimeDispatchResult =
            runtime.dispatch(build(RuntimeEventId(nextId++)))

        fun play(vararg uci: String) = uci.forEach { move ->
            dispatch { RuntimeEvent.HumanMove(it, Move.parseUci(move)) }
        }

        val terminal: RuntimeTerminal? get() = runtime.state.terminal
    }

    @Test
    fun captureLeavingKingAndKnightVersusKingIsAnAutomaticDraw() {
        val table = Table("4k3/8/8/8/8/2n5/4N3/4K3 w - - 0 1")

        table.play("e2c3")

        assertEquals(RuntimeTerminal.InsufficientMaterial, table.terminal)
        assertEquals(GameResult.DRAW, table.runtime.state.gameTree.result)
    }

    @Test
    fun sufficientMaterialKeepsTheGameGoing() {
        val table = Table("4k3/8/8/8/8/2n5/4N3/R3K3 w - - 0 1")

        table.play("e2c3")

        assertNull(table.terminal)
    }

    @Test
    fun thirdOccurrenceOfTheStartingPositionEndsTheGameAtOnce() {
        val table = Table(null)

        table.play("g1f3", "g8f6", "f3g1", "f6g8")
        assertNull(table.terminal, "second occurrence is not yet a draw")
        table.play("g1f3", "g8f6", "f3g1")
        assertNull(table.terminal)
        table.play("f6g8")

        assertEquals(RuntimeTerminal.ThreefoldRepetition, table.terminal)
        assertEquals(GameResult.DRAW, table.runtime.state.gameTree.result)
        assertEquals(8, table.runtime.state.gameTree.mainline().size)
    }

    @Test
    fun repetitionAlsoWorksFromACustomStartPosition() {
        val table = Table("4k3/8/8/8/3p4/8/8/R3K1N1 w - - 0 1")

        table.play("g1f3", "e8e7", "f3g1", "e7e8", "g1f3", "e8e7", "f3g1", "e7e8")

        assertEquals(RuntimeTerminal.ThreefoldRepetition, table.terminal)
    }

    @Test
    fun repetitionWindowStartsAtTheLastCaptureAndIncludesIt() {
        val table = Table(null)

        // 1.e4 d5 2.exd5 Qxd5 is irreversible. The position right after it (ply 4) recurs at ply 8
        // and ply 12: the third occurrence sits exactly on the edge of the reversible window.
        table.play("e2e4", "d7d5", "e4d5", "d8d5", "b1c3", "d5d8", "c3b1", "d8d5")
        assertNull(table.terminal, "second occurrence must not end the game")
        table.play("b1c3", "d5d8", "c3b1")
        assertNull(table.terminal)
        table.play("d8d5")

        assertEquals(RuntimeTerminal.ThreefoldRepetition, table.terminal)
    }

    @Test
    fun aPositionSeenBeforeACaptureCannotRepeatAfterIt() {
        val table = Table("4k3/8/8/8/8/2n5/4N3/R3K3 w - - 0 1")

        // Knight trade-offs change the material, so nothing before the capture may be counted.
        table.play("e2c3", "e8e7", "c3e2", "e7e8", "e2c3", "e8e7", "c3e2", "e7e8")

        assertNull(table.terminal)
    }

    @Test
    fun fiftyMoveRuleEndsTheGameOnTheHundredthHalfMove() {
        val table = Table("4k3/8/8/8/8/8/8/R3K3 w - - 99 60")

        table.play("a1a2")

        assertEquals(RuntimeTerminal.FiftyMoveRule, table.terminal)
        assertEquals(GameResult.DRAW, table.runtime.state.gameTree.result)
    }

    @Test
    fun checkmateOnTheHundredthHalfMoveStillWins() {
        val table = Table("7k/8/6K1/8/8/8/8/R7 w - - 99 60")

        table.play("a1a8")

        assertEquals(RuntimeTerminal.Checkmate(dev.lumenchess.core.chess.Color.WHITE), table.terminal)
        assertEquals(GameResult.WHITE_WIN, table.runtime.state.gameTree.result)
    }

    @Test
    fun flaggingAgainstAnOpponentWhoCannotMateIsADraw() {
        val table = Table("4k3/8/8/8/8/8/8/1N2K3 b - - 0 1", clockMillis = 1_000L)

        table.time.advanceBy(2_000L)
        table.dispatch { RuntimeEvent.ClockCheck(it) }

        assertEquals(RuntimeTerminal.InsufficientMaterial, table.terminal)
        assertEquals(GameResult.DRAW, table.runtime.state.gameTree.result)
    }

    @Test
    fun flaggingAgainstAnOpponentWhoCanMateStillLoses() {
        listOf(
            "4k3/8/8/8/8/8/8/1R2K3 b - - 0 1", // rook
            "4k3/8/8/8/8/8/8/1NN1K3 b - - 0 1", // two knights
            "4k3/4p3/8/8/8/8/8/1N2K3 b - - 0 1", // flagged side still has a pawn
        ).forEach { fen ->
            val table = Table(fen, clockMillis = 1_000L)

            table.time.advanceBy(2_000L)
            table.dispatch { RuntimeEvent.ClockCheck(it) }

            assertEquals(
                RuntimeTerminal.Timeout(dev.lumenchess.core.chess.Color.BLACK),
                table.terminal,
                "unexpected outcome for $fen",
            )
        }
    }
}
