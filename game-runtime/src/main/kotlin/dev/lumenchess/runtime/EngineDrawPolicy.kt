package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.math.abs

sealed interface DrawOfferResponse {
    data object Accepted : DrawOfferResponse
    data class Declined(val reason: String) : DrawOfferResponse
}

/**
 * How an engine opponent answers a human draw offer.
 *
 * The decision is deterministic for a given game (recorded seed + number of plies played), so
 * asking again in the same position cannot re-roll it. It only reads material and game length; it
 * never touches the runtime, which stays the sole owner of the result.
 */
object EngineDrawPolicy {
    const val MIN_PLIES_BEFORE_DRAW = 30

    /** Offers made within this many plies of the previous one are not entertained. */
    const val OFFER_COOLDOWN_PLIES = 10

    fun respond(
        state: RuntimeState,
        engineSide: Color,
        strength: EngineStrengthSettings,
        plies: Int = state.gameTree.mainline().size,
    ): DrawOfferResponse {
        if (plies < MIN_PLIES_BEFORE_DRAW) return DrawOfferResponse.Declined("It is too early to agree a draw")

        val advantage = materialBalance(state, engineSide)
        if (advantage >= 1.5) return DrawOfferResponse.Declined("The engine is better and plays on")
        if (advantage <= -1.5) return DrawOfferResponse.Accepted

        val elo = (strength.target as? EngineStrengthTarget.Elo)?.value
        val discipline = EngineThinkTimePolicy.clockDiscipline(elo)
        // Level positions: weaker opponents settle more readily, full strength rarely does.
        val acceptChance = 0.75 - 0.40 * discipline
        return if (roll(strength.seed, plies) < acceptChance) {
            DrawOfferResponse.Accepted
        } else {
            DrawOfferResponse.Declined("The engine declined the draw")
        }
    }

    /** Positive when [side] is ahead, in pawns (P=1, N=B=3, R=5, Q=9). */
    fun materialBalance(state: RuntimeState, side: Color): Double {
        var own = 0
        var other = 0
        state.position.board.forEach { piece ->
            if (piece == null) return@forEach
            val value = when (piece.type) {
                PieceType.PAWN -> 1
                PieceType.KNIGHT, PieceType.BISHOP -> 3
                PieceType.ROOK -> 5
                PieceType.QUEEN -> 9
                PieceType.KING -> 0
            }
            if (piece.color == side) own += value else other += value
        }
        return (own - other).toDouble()
    }

    private fun roll(seed: Long, plies: Int): Double {
        var z = seed xor (plies.toLong() * -7046029254386353131L)
        z += -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return ((z ushr 11).toDouble() + 0.5) / (1L shl 53).toDouble()
    }

    /** True when an offer made at [plies] is still inside the cooldown from [lastOfferPly]. */
    fun inCooldown(lastOfferPly: Int?, plies: Int): Boolean =
        lastOfferPly != null && abs(plies - lastOfferPly) < OFFER_COOLDOWN_PLIES
}
