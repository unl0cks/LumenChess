package dev.lumenchess.feedback

import java.io.File

sealed interface ResolvedSoundSource {
    data class BuiltIn(val event: SoundEvent) : ResolvedSoundSource
    data class LocalFile(val file: File) : ResolvedSoundSource
}

/** Resolves app-private overrides/packs without ever crossing into runtime authority. */
class SoundSourceResolver(private val feedbackRoot: File) {
    fun resolve(event: SoundEvent, soundPackId: String): ResolvedSoundSource {
        findEventFile(File(feedbackRoot, "overrides"), event)?.let {
            return ResolvedSoundSource.LocalFile(it)
        }

        if (soundPackId != BUILT_IN_PACK_ID && PACK_ID.matches(soundPackId)) {
            findEventFile(File(feedbackRoot, "sound-packs/$soundPackId"), event)?.let {
                return ResolvedSoundSource.LocalFile(it)
            }
        }
        return ResolvedSoundSource.BuiltIn(event)
    }

    private fun findEventFile(root: File, event: SoundEvent): File? {
        if (!root.isDirectory) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        for (extension in EXTENSION_PRIORITY) {
            val candidate = File(canonicalRoot, "${event.fileStem}.$extension")
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: continue
            if (canonical.parentFile == canonicalRoot && canonical.isFile) return canonical
        }
        return null
    }

    companion object {
        const val BUILT_IN_PACK_ID = "lumen-built-in"
        private val PACK_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val EXTENSION_PRIORITY = listOf("wav", "ogg", "mp3", "webm")
    }
}
