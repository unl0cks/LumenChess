package dev.lumenchess.settings

import dev.lumenchess.feedback.FeedbackSettings

/** Compatibility symbol for presentation callers; the member function remains authoritative. */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AppearanceSettings.toFeedbackSettings(): FeedbackSettings = FeedbackSettings(
    soundsEnabled = feedbackSoundsEnabled,
    hapticsEnabled = feedbackHapticsEnabled,
    soundEvents = feedbackSoundEvents,
    hapticEvents = feedbackHapticEvents,
)
