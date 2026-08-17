package dev.lumenchess.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

/** Low-latency presentation-only sound adapter backed by project-owned synthesized audio. */
class SoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val feedbackRoot = File(appContext.filesDir, "feedback")
    private val builtInRoot = File(feedbackRoot, "built-in")
    private val resolver = SoundSourceResolver(feedbackRoot)
    private val lock = Any()
    private val loadedSamples = mutableSetOf<Int>()
    private val pendingSamples = mutableSetOf<Int>()
    private val samplesByPath = mutableMapOf<String, Int>()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            val shouldPlay = synchronized(lock) {
                loadedSamples += sampleId
                pendingSamples.remove(sampleId)
            }
            if (shouldPlay) soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun play(event: SoundEvent, soundPackId: String = SoundSourceResolver.BUILT_IN_PACK_ID) {
        val file = when (val source = resolver.resolve(event, soundPackId)) {
            is ResolvedSoundSource.BuiltIn -> runCatching {
                BuiltInSoundAssets.ensure(builtInRoot, source.event)
            }.getOrNull()
            is ResolvedSoundSource.LocalFile -> source.file
        } ?: return
        val sampleId = sample(file) ?: return

        val loaded = synchronized(lock) {
            if (sampleId in loadedSamples) true else {
                pendingSamples += sampleId
                false
            }
        }
        if (loaded) soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
    }

    fun close() {
        synchronized(lock) {
            pendingSamples.clear()
            loadedSamples.clear()
            samplesByPath.clear()
        }
        soundPool.release()
    }

    private fun sample(file: File): Int? {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return null
        synchronized(lock) { samplesByPath[path] }?.let { return it }
        val loaded = runCatching { soundPool.load(path, 1) }.getOrNull() ?: return null
        if (loaded == 0) return null
        synchronized(lock) { samplesByPath[path] = loaded }
        return loaded
    }
}
