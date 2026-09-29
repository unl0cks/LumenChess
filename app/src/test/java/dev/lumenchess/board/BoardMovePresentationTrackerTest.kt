package dev.lumenchess.board

import kotlin.test.Test
import kotlin.test.assertEquals

class BoardMovePresentationTrackerTest {
    @Test fun aHumanMoveKeepsItsPresentationAcrossLaterRecompositions() {
        val tracker = BoardMovePresentationTracker(initialRevision = 4)
        assertEquals(BoardMovePresentation.HUMAN_TAP, tracker.presentationFor(5, lastMoverIsHuman = true))
        // The Live clock recomposes every 100 ms with the same revision. The presentation is a key of
        // the board's motion effect, so it must not change until a new revision arrives.
        repeat(10) {
            assertEquals(BoardMovePresentation.HUMAN_TAP, tracker.presentationFor(5, lastMoverIsHuman = true))
        }
    }

    @Test fun eachNewRevisionIsClassifiedOnce() {
        val tracker = BoardMovePresentationTracker(initialRevision = 0)
        assertEquals(BoardMovePresentation.ENGINE, tracker.presentationFor(0, lastMoverIsHuman = true))
        assertEquals(BoardMovePresentation.HUMAN_TAP, tracker.presentationFor(1, lastMoverIsHuman = true))
        assertEquals(BoardMovePresentation.ENGINE, tracker.presentationFor(2, lastMoverIsHuman = false))
        assertEquals(BoardMovePresentation.ENGINE, tracker.presentationFor(2, lastMoverIsHuman = false))
        // An engine reply and the queued premove land in one update: two revisions at once.
        assertEquals(BoardMovePresentation.PREMOVE, tracker.presentationFor(4, lastMoverIsHuman = true))
        assertEquals(BoardMovePresentation.PREMOVE, tracker.presentationFor(4, lastMoverIsHuman = true))
    }

    @Test fun aLowerRevisionIsADifferentGameAndIsNotAnimated() {
        val tracker = BoardMovePresentationTracker(initialRevision = 30)
        assertEquals(BoardMovePresentation.HUMAN_TAP, tracker.presentationFor(31, lastMoverIsHuman = true))
        assertEquals(BoardMovePresentation.ENGINE, tracker.presentationFor(0, lastMoverIsHuman = true))
        assertEquals(BoardMovePresentation.HUMAN_TAP, tracker.presentationFor(1, lastMoverIsHuman = true))
    }
}
