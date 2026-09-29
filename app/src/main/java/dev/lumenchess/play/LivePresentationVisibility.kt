package dev.lumenchess.play

/**
 * Presentation-only visibility contract for the focused Human-vs-Engine workspace.
 *
 * The serialized M17 runtime remains authoritative for game state, clocks, moves,
 * pause/resume and engine coordination. These flags only decide which optional UI
 * surfaces are emitted. Persistence/user-facing controls belong to the later Play /
 * assistance customization milestone rather than P5.
 */
internal data class LivePresentationVisibility(
    val showCapturedMaterial: Boolean = false,
    val showPauseButton: Boolean = false,
)

internal val DefaultLivePresentationVisibility = LivePresentationVisibility()
