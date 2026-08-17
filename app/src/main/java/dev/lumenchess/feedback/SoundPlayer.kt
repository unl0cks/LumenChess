package dev.lumenchess.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.lumenchess.R
import java.io.File

/** Low-latency presentation-only sound adapter backed by project-owned bundled audio. */
class SoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = SoundSourceResolver(File(appContext.filesDir, "feedback"))
    private val lock = Any()
    private val loadedSamples = mutableSetOf<Int>()
    private val pendingSamples = mutableSetOf<Int>()
    private val localSamples = mutableMapOf<String, Int>()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val builtInSamples: Map<SoundEvent, Int>

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            val shouldPlay = synchronized(lock) {
                loadedSamples += sampleId
                pendingSamples.remove(sampleId)
            }
            if (shouldPlay) soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
        }
        builtInSamples = BUILT_IN_RESOURCES.mapValues { (_, resourceId) ->
            soundPool.load(appContext, resourceId, 1)
        }
    }

    fun play(event: SoundEvent, soundPackId: String = SoundSourceResolver.BUILT_IN_PACK_ID) {
        val sampleId = when (val source = resolver.resolve(event, soundPackId)) {
            is ResolvedSoundSource.BuiltIn -> builtInSamples[source.event]
            is ResolvedSoundSource.LocalFile -> localSample(source.file)
        } ?: return

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
            localSamples.clear()
        }
        soundPool.release()
    }

    private fun localSample(file: File): Int? {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return null
        synchronized(lock) { localSamples[path] }?.let { return it }
        val loaded = runCatching { soundPool.load(path, 1) }.getOrNull() ?: return null
        if (loaded == 0) return null
        synchronized(lock) { localSamples[path] = loaded }
        return loaded
    }

    companion object {
        private val BUILT_IN_RESOURCES = mapOf(
            SoundEvent.MOVE to R.raw.lumen_move,
            SoundEvent.CAPTURE to R.raw.lumen_capture,
            SoundEvent.CHECK to R.raw.lumen_check,
            SoundEvent.CASTLE to R.raw.lumen_castle,
            SoundEvent.PROMOTION to R.raw.lumen_promotion,
            SoundEvent.GAME_START to R.raw.lumen_game_start,
            SoundEvent.GAME_END to R.raw.lumen_game_end,
        )
    }
}
