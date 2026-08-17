package dev.lumenchess.feedback

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundPackImporterTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun wavHeader(): ByteArray = "RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray()
    private fun oggHeader(): ByteArray = "OggS".toByteArray()
    private fun mp3Header(): ByteArray = "ID3".toByteArray()
    private fun tempRoot(): File = Files.createTempDirectory("lumen-sound-pack-test").toFile()

    @Test
    fun validPackExtractsOnlyNamedFeedbackEventsInsideDestination() {
        val root = tempRoot()
        val pack = SoundPackImporter().importZip(
            input = ByteArrayInputStream(
                zip(
                    "move.wav" to wavHeader(),
                    "capture.ogg" to oggHeader(),
                    "check.mp3" to mp3Header(),
                ),
            ),
            destinationRoot = root,
            packId = "my-pack",
        )

        assertEquals(setOf(SoundEvent.MOVE, SoundEvent.CAPTURE, SoundEvent.CHECK), pack.files.keys)
        assertTrue(pack.root.canonicalPath.startsWith(root.canonicalPath + File.separator))
        pack.files.values.forEach { file ->
            assertTrue(file.exists())
            assertTrue(file.canonicalPath.startsWith(pack.root.canonicalPath + File.separator))
        }
    }

    @Test
    fun zipSlipTraversalIsRejectedAndCannotWriteOutsidePackRoot() {
        val root = tempRoot()
        val outside = File(root.parentFile, "escape.wav")
        outside.delete()

        assertFailsWith<IllegalArgumentException> {
            SoundPackImporter().importZip(
                ByteArrayInputStream(zip("../escape.wav" to wavHeader())),
                root,
                "bad-pack",
            )
        }

        assertFalse(outside.exists())
    }

    @Test
    fun absoluteAndNestedEventPathsAreRejected() {
        val importer = SoundPackImporter()
        val root = tempRoot()

        assertFailsWith<IllegalArgumentException> {
            importer.importZip(ByteArrayInputStream(zip("/move.wav" to wavHeader())), root, "absolute")
        }
        assertFailsWith<IllegalArgumentException> {
            importer.importZip(ByteArrayInputStream(zip("folder/move.wav" to wavHeader())), root, "nested")
        }
    }

    @Test
    fun unsupportedExtensionsAreRejectedInsteadOfBeingCopiedBlindly() {
        assertFailsWith<IllegalArgumentException> {
            SoundPackImporter().importZip(
                ByteArrayInputStream(zip("move.exe" to byteArrayOf(1, 2, 3))),
                tempRoot(),
                "unsupported",
            )
        }
    }

    @Test
    fun supportedExtensionWithInvalidContainerDataIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SoundPackImporter().importZip(
                ByteArrayInputStream(zip("move.wav" to "not-a-wave".toByteArray())),
                tempRoot(),
                "invalid-media",
            )
        }
    }

    @Test
    fun malformedZipIsRejectedAndCleanedUp() {
        val root = tempRoot()
        val finalRoot = File(root, "malformed")

        assertFailsWith<IllegalArgumentException> {
            SoundPackImporter().importZip(
                ByteArrayInputStream("definitely-not-a-zip".toByteArray()),
                root,
                "malformed",
            )
        }

        assertFalse(finalRoot.exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".malformed.import-") })
    }

    @Test
    fun duplicateEventAssignmentsAreRejectedEvenAcrossExtensionsAndCase() {
        assertFailsWith<IllegalArgumentException> {
            SoundPackImporter().importZip(
                ByteArrayInputStream(
                    zip(
                        "move.wav" to wavHeader(),
                        "MOVE.ogg" to oggHeader(),
                    ),
                ),
                tempRoot(),
                "duplicate",
            )
        }
    }

    @Test
    fun actualReadBytesEnforcePerEntryAndAggregateCaps() {
        val root = tempRoot()
        val importer = SoundPackImporter(maxEntryBytes = 12, maxTotalBytes = 20)

        assertFailsWith<IllegalArgumentException> {
            importer.importZip(
                ByteArrayInputStream(zip("move.wav" to ByteArray(13) { 1 })),
                root,
                "entry-too-large",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            importer.importZip(
                ByteArrayInputStream(
                    zip(
                        "move.wav" to wavHeader(),
                        "capture.wav" to wavHeader(),
                    ),
                ),
                root,
                "pack-too-large",
            )
        }
    }

    @Test
    fun failedImportDoesNotLeavePartialPackBehind() {
        val root = tempRoot()
        val destination = File(root, "partial")

        assertFailsWith<IllegalArgumentException> {
            SoundPackImporter(maxEntryBytes = 12).importZip(
                ByteArrayInputStream(
                    zip(
                        "move.wav" to wavHeader(),
                        "capture.wav" to ByteArray(13) { 1 },
                    ),
                ),
                root,
                "partial",
            )
        }

        assertFalse(destination.exists())
    }

    @Test
    fun singleEventImportCopiesSupportedAudioIntoPrivateRoot() {
        val root = tempRoot()
        val bytes = wavHeader()
        val file = SoundPackImporter(maxEntryBytes = 12).importSingle(
            input = ByteArrayInputStream(bytes),
            originalFileName = "whatever.WAV",
            destinationRoot = root,
            event = SoundEvent.PROMOTION,
        )

        assertTrue(file.exists())
        assertTrue(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertContentEquals(bytes, file.readBytes())
        assertEquals("promotion.wav", file.name)
    }

    @Test
    fun singleEventImportRejectsUnsupportedInvalidAndOversizePayloads() {
        val importer = SoundPackImporter(maxEntryBytes = 12)
        val root = tempRoot()

        assertFailsWith<IllegalArgumentException> {
            importer.importSingle(ByteArrayInputStream(byteArrayOf(1)), "sound.txt", root, SoundEvent.MOVE)
        }
        assertFailsWith<IllegalArgumentException> {
            importer.importSingle(
                ByteArrayInputStream("not-a-wave".toByteArray()),
                "sound.wav",
                root,
                SoundEvent.MOVE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            importer.importSingle(ByteArrayInputStream(ByteArray(13)), "sound.ogg", root, SoundEvent.MOVE)
        }
    }
}
