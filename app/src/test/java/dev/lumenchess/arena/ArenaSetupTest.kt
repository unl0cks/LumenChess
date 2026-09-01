package dev.lumenchess.arena

import dev.lumenchess.core.chess.Chess960
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.play.PlayEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ArenaSetupTest {
    @Test
    fun independentEngineConfigurationsSurviveResolution() {
        val setup = ArenaSetupConfig(
            white = ArenaEngineConfig(
                engine = PlayEngine.STOCKFISH_18,
                strengthModel = EngineStrengthModel.ENGINE_NATIVE,
                strengthTarget = EngineStrengthTarget.Elo(1900),
                strengthSeed = 11,
            ),
            black = ArenaEngineConfig(
                engine = PlayEngine.RECKLESS_0_9_0,
                strengthModel = EngineStrengthModel.HUMANIZED,
                strengthTarget = EngineStrengthTarget.Elo(1200),
                strengthSeed = 22,
            ),
        )

        val resolved = ArenaSetupResolver.resolve(setup, randomInt = { 0 })

        assertEquals(PlayEngine.STOCKFISH_18, resolved.white.engine)
        assertEquals(EngineStrengthTarget.Elo(1900), resolved.white.strength.target)
        assertEquals(PlayEngine.RECKLESS_0_9_0, resolved.black.engine)
        assertEquals(EngineStrengthModel.HUMANIZED, resolved.black.strength.model)
    }

    @Test
    fun randomColorAssignmentSwapsTheCompleteEngineConfigurations() {
        val setup = ArenaSetupConfig(
            white = ArenaEngineConfig(engine = PlayEngine.STOCKFISH_18, strengthSeed = 7),
            black = ArenaEngineConfig(engine = PlayEngine.RECKLESS_0_9_0, strengthSeed = 9),
            colorAssignment = ArenaColorAssignment.RANDOM,
        )

        val resolved = ArenaSetupResolver.resolve(setup, randomInt = { bound -> bound - 1 })

        assertEquals(PlayEngine.RECKLESS_0_9_0, resolved.white.engine)
        assertEquals(9L, resolved.white.strength.seed)
        assertEquals(PlayEngine.STOCKFISH_18, resolved.black.engine)
        assertEquals(7L, resolved.black.strength.seed)
    }

    @Test
    fun invalidCustomFenIsRejectedBeforeArenaStarts() {
        val setup = ArenaSetupConfig(
            opening = ArenaOpeningSetup(ArenaOpeningMode.CUSTOM_FEN, customFen = "not fen"),
        )

        val validation = ArenaSetupValidator.validate(setup)

        assertInstanceOf(ArenaSetupValidation.Invalid::class.java, validation)
    }

    @Test
    fun randomOpeningUsesRequestedHandoffDepthAndRemainsLegal() {
        val setup = ArenaSetupConfig(
            opening = ArenaOpeningSetup(ArenaOpeningMode.RANDOM_OPENING, handoffPlies = 8),
        )

        val resolved = ArenaSetupResolver.resolve(setup, randomInt = { 0 })

        assertEquals(8, resolved.opening.appliedMoves.size)
        assertEquals(Variant.STANDARD, resolved.variant)
        assertEquals(5, resolved.initialPosition.fullmoveNumber)
        assertEquals(Fen.serialize(resolved.opening.position), Fen.serialize(resolved.initialPosition))
    }

    @Test
    fun everyBundledOpeningLineIsLegalThroughTheMaximumHandoff() {
        ArenaOpeningCatalog.lines.indices.forEach { index ->
            val opening = ArenaOpeningCatalog.resolveRandom(handoffPlies = 12, randomInt = { index })

            assertEquals(12, opening.appliedMoves.size, ArenaOpeningCatalog.lines[index].name)
        }
    }

    @Test
    fun openingFamilySelectsOnlyThatFamily() {
        val setup = ArenaSetupConfig(
            opening = ArenaOpeningSetup(
                mode = ArenaOpeningMode.OPENING_FAMILY,
                familyId = "queens-pawn",
                handoffPlies = 6,
            ),
        )

        val resolved = ArenaSetupResolver.resolve(setup, randomInt = { 0 })

        assertEquals("queens-pawn", resolved.opening.familyId)
        assertEquals(6, resolved.opening.appliedMoves.size)
        assertEquals("d2d4", resolved.opening.appliedMoves.first().uci)
    }

    @Test
    fun randomChess960UsesBoundedGeneratedIndex() {
        val setup = ArenaSetupConfig(
            variant = Variant.CHESS960,
            opening = ArenaOpeningSetup(ArenaOpeningMode.RANDOM_CHESS960),
        )

        val resolved = ArenaSetupResolver.resolve(setup, randomInt = { bound -> bound - 1 })

        assertEquals(959, resolved.chess960Index)
        assertEquals(
            Fen.serialize(Chess960.startingPosition(959)),
            Fen.serialize(resolved.initialPosition),
        )
    }

    @Test
    fun standardOpeningBookCannotSilentlyReplaceAChess960Setup() {
        val setup = ArenaSetupConfig(
            variant = Variant.CHESS960,
            chess960Index = 42,
            opening = ArenaOpeningSetup(ArenaOpeningMode.RANDOM_OPENING),
        )

        assertInstanceOf(ArenaSetupValidation.Invalid::class.java, ArenaSetupValidator.validate(setup))
    }

    @Test
    fun normalStartRemainsTheDefault() {
        val resolved = ArenaSetupResolver.resolve(ArenaSetupConfig(), randomInt = { 0 })

        assertEquals(ArenaOpeningMode.NORMAL, resolved.opening.mode)
        assertEquals(Fen.serialize(Position.initial()), Fen.serialize(resolved.initialPosition))
    }
}
