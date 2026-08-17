package dev.lumenchess.feedback

import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

enum class SoundEvent(val fileStem: String) {
    MOVE("move"),
    CAPTURE("capture"),
    CHECK("check"),
    CASTLE("castle"),
    PROMOTION("promotion"),
    GAME_START("game_start"),
    GAME_END("game_end");

    companion object {
        fun fromFileStem(stem: String): SoundEvent? = entries.firstOrNull {
            it.fileStem.equals(stem, ignoreCase = true)
        }
    }
}

data class SoundPack(
    val id: String,
    val root: File,
    val files: Map<SoundEvent, File>,
)

class SoundPackImporter(
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
) {
    init {
        require(maxEntryBytes > 0) { "maxEntryBytes must be positive" }
        require(maxTotalBytes > 0) { "maxTotalBytes must be positive" }
        require(maxTotalBytes >= maxEntryBytes) { "maxTotalBytes must be at least maxEntryBytes" }
    }

    fun importZip(
        input: InputStream,
        destinationRoot: File,
        packId: String,
    ): SoundPack {
        require(PACK_ID.matches(packId)) { "Invalid sound pack id" }
        destinationRoot.mkdirs()
        require(destinationRoot.isDirectory) { "Sound pack destination is not a directory" }

        val finalRoot = File(destinationRoot, packId)
        require(isDirectChild(destinationRoot, finalRoot)) { "Invalid sound pack destination" }
        require(!finalRoot.exists()) { "Sound pack already exists" }

        val stagingRoot = File(destinationRoot, ".$packId.import-${UUID.randomUUID()}")
        require(isDirectChild(destinationRoot, stagingRoot)) { "Invalid staging destination" }
        require(stagingRoot.mkdir()) { "Could not create sound pack staging directory" }

        val imported = linkedMapOf<SoundEvent, File>()
        var totalBytes = 0L

        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Directories are not allowed in sound packs" }

                    val parsed = parseEntryName(entry.name)
                    require(parsed.event !in imported) { "Duplicate sound event mapping: ${parsed.event.fileStem}" }

                    val output = File(stagingRoot, parsed.canonicalName)
                    require(isDirectChild(stagingRoot, output)) { "Unsafe sound pack path" }
                    val bytesWritten = copyBounded(zip, output, maxEntryBytes) { chunk ->
                        totalBytes += chunk
                        require(totalBytes <= maxTotalBytes) { "Sound pack exceeds total size limit" }
                    }
                    require(bytesWritten <= maxEntryBytes) { "Sound exceeds per-entry size limit" }
                    SoundMediaValidator.requireSupported(output)
                    imported[parsed.event] = output
                    zip.closeEntry()
                }
            }

            require(imported.isNotEmpty()) { "Sound pack contains no supported feedback sounds" }
            require(stagingRoot.renameTo(finalRoot)) { "Could not publish sound pack" }

            val published = imported.mapValues { (_, staged) -> File(finalRoot, staged.name) }
            return SoundPack(packId, finalRoot, published)
        } catch (error: IllegalArgumentException) {
            stagingRoot.deleteRecursively()
            finalRoot.deleteRecursively()
            throw error
        } catch (error: Exception) {
            stagingRoot.deleteRecursively()
            finalRoot.deleteRecursively()
            throw IllegalArgumentException("Could not import sound pack", error)
        }
    }

    fun importSingle(
        input: InputStream,
        originalFileName: String,
        destinationRoot: File,
        event: SoundEvent,
    ): File {
        val parsedExtension = validatedExtension(originalFileName)
        require(!originalFileName.contains('/') && !originalFileName.contains('\\')) {
            "Nested sound paths are not allowed"
        }

        destinationRoot.mkdirs()
        require(destinationRoot.isDirectory) { "Sound destination is not a directory" }

        val target = File(destinationRoot, "${event.fileStem}.$parsedExtension")
        require(isDirectChild(destinationRoot, target)) { "Unsafe sound destination" }
        val staging = File(destinationRoot, ".${event.fileStem}.import-${UUID.randomUUID()}.$parsedExtension")
        require(isDirectChild(destinationRoot, staging)) { "Unsafe sound staging path" }

        try {
            copyBounded(input, staging, maxEntryBytes)
            SoundMediaValidator.requireSupported(staging)
            if (target.exists()) {
                require(target.delete()) { "Could not replace existing sound" }
            }
            require(staging.renameTo(target)) { "Could not publish imported sound" }
            return target
        } catch (error: IllegalArgumentException) {
            staging.delete()
            throw error
        } catch (error: Exception) {
            staging.delete()
            throw IllegalArgumentException("Could not import sound", error)
        }
    }

    private data class ParsedEntry(
        val event: SoundEvent,
        val canonicalName: String,
    )

    private fun parseEntryName(name: String): ParsedEntry {
        require(name.isNotBlank()) { "Empty sound pack entry" }
        require(!name.startsWith('/') && !name.startsWith('\\')) { "Absolute paths are not allowed" }
        require(!name.contains('/') && !name.contains('\\')) { "Nested sound paths are not allowed" }
        require(name != "." && name != "..") { "Traversal path is not allowed" }

        val extension = validatedExtension(name)
        val dot = name.lastIndexOf('.')
        val stem = name.substring(0, dot)
        val event = SoundEvent.fromFileStem(stem)
            ?: throw IllegalArgumentException("Unknown feedback sound event: $stem")
        return ParsedEntry(event, "${event.fileStem}.$extension")
    }

    private fun validatedExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        require(dot > 0 && dot < name.lastIndex) { "Sound file must have a supported extension" }
        val extension = name.substring(dot + 1).lowercase(Locale.ROOT)
        require(extension in SUPPORTED_EXTENSIONS) { "Unsupported sound extension: $extension" }
        return extension
    }

    private fun copyBounded(
        input: InputStream,
        output: File,
        byteLimit: Long,
        onChunk: (Long) -> Unit = {},
    ): Long {
        var total = 0L
        output.outputStream().use { sink ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read.toLong()
                require(total <= byteLimit) { "Sound exceeds per-entry size limit" }
                onChunk(read.toLong())
                sink.write(buffer, 0, read)
            }
        }
        return total
    }

    private fun isDirectChild(parent: File, child: File): Boolean =
        child.canonicalFile.parentFile == parent.canonicalFile

    companion object {
        private val PACK_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val SUPPORTED_EXTENSIONS = setOf("wav", "ogg", "mp3", "webm")
        private const val DEFAULT_MAX_ENTRY_BYTES = 4L * 1024L * 1024L
        private const val DEFAULT_MAX_TOTAL_BYTES = 24L * 1024L * 1024L
    }
}
