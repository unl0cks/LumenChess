package dev.lumenchess.play

// Stable semantic tags shared by the Play surfaces and the instrumented tests. The earlier
// experimental Play/Live screens that lived beside these were unreachable and have been removed;
// the reachable Play UI is ReferencePlayRoute (Overview / New Game) and BoardFirstReferenceLiveScreen.
const val PLAY_SETUP_TEST_TAG = "play-setup"
const val PLAY_LIVE_TEST_TAG = "play-live"
const val PLAY_START_TEST_TAG = "play-start"
const val PLAY_RESUME_TEST_TAG = "play-resume"
const val PLAY_ENGINE_STATUS_TEST_TAG = "play-engine-status"
const val PLAY_PREMOVE_OVERLAY_TEST_TAG = "play-premove-overlay"
const val PLAY_BOARD_STAGE_TEST_TAG = "play-board-stage"
