package dev.lumenchess.feedback

import java.io.File
import java.util.Locale

/** Lightweight container-signature validation for user-provided feedback audio. */
internal object SoundMediaValidator {
    fun requireSupported(file: File) {
        require(isSupported(file)) { "Invalid or unsupported sound data: ${file.name}" }
    }

    fun isSupported(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val extension = file.extension.lowercase(Locale.ROOT)
        return runCatching {
            file.inputStream().buffered().use { input ->
                val header = ByteArray(12)
                var size = 0
                while (size < header.size) {
                    val read = input.read(header, size, header.size - size)
                    if (read < 0) break
                    if (read == 0) continue
                    size += read
                }
                matches(extension, header, size)
            }
        }.getOrDefault(false)
    }

    private fun matches(extension: String, header: ByteArray, size: Int): Boolean = when (extension) {
        "wav" -> size >= 12 && ascii(header, 0, "RIFF") && ascii(header, 8, "WAVE")
        "ogg" -> size >= 4 && ascii(header, 0, "OggS")
        "mp3" -> size >= 3 && (
            ascii(header, 0, "ID3") ||
                ((header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0)
            )
        "webm" -> size >= 4 &&
            (header[0].toInt() and 0xFF) == 0x1A &&
            (header[1].toInt() and 0xFF) == 0x45 &&
            (header[2].toInt() and 0xFF) == 0xDF &&
            (header[3].toInt() and 0xFF) == 0xA3
        else -> false
    }

    private fun ascii(bytes: ByteArray, offset: Int, value: String): Boolean =
        value.indices.all { index -> bytes[offset + index] == value[index].code.toByte() }
}
