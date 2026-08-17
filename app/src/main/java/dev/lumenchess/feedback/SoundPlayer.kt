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
    private val builtInFallbackBySample = mutableMapOf<Int, SoundEvent>()
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
            var shouldPlay = false
            var fallback: SoundEvent? = null
            synchronized(lock) {
                if (status == 0) {
                    loadedSamples += sampleId
                    shouldPlay = pendingSamples.remove(sampleId)
                    builtInFallbackBySample.remove(sampleId)
                } else {
                    pendingSamples.remove(sampleId)
                    fallback = builtInFallbackBySample.remove(sampleId)
                    samplesByPath.entries.removeAll { it.value == sampleId }
                }
            }
            if (status == 0 && shouldPlay) {
                playSample(sampleId)
            } else if (status != 0) {
                fallback?.let(::playBuiltIn)
            }
        }
    }

    fun play(event: SoundEvent, soundPackId: String = SoundSourceResolver.BUILT_IN_PACK_ID) {
        when (val source = resolver.resolve(event, soundPackId)) {
            is ResolvedSoundSource.BuiltIn -> playBuiltIn(source.event)
            is ResolvedSoundSource.LocalFile -> playFile(source.file, fallback = event)
        }
    }

    fun close() {
        synchronized(lock) {
            pendingSamples.clear()
            loadedSamples.clear()
            samplesByPath.clear()
            builtInFallbackBySample.clear()
        }
        runCatching { soundPool.release() }
    }

    private fun playBuiltIn(event: SoundEvent) {
        val file = runCatching { BuiltInSoundAssets.ensure(builtInRoot, event) }.getOrNull() ?: return
        playFile(file, fallback = null)
    }

    private fun playFile(file: File, fallback: SoundEvent?) {
        val sampleId = sample(file)
        if (sampleId == null) {
            fallback?.let(::playBuiltIn)
            return
        }

        val loaded = synchronized(lock) {
            if (sampleId in loadedSamples) {
                true
            } else {
                pendingSamples += sampleId
                if (fallback != null) builtInFallbackBySample[sampleId] = fallback
                false
            }
        }
        if (loaded) playSample(sampleId)
    }

    private fun playSample(sampleId: Int) {
        runCatching { soundPool.play(sampleId, 1f, 1f, 1, 0, 1f) }
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
