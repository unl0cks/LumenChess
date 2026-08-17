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

    private fun tempRoot(): File = Files.createTempDirectory("lumen-sound-pack-test").toFile()

    @Test
    fun validPackExtractsOnlyNamedFeedbackEventsInsideDestination() {
        val root = tempRoot()
        val pack = SoundPackImporter().importZip(
            input = ByteArrayInputStream(
                zip(
                    "move.wav" to byteArrayOf(1, 2, 3),
                    "capture.ogg" to byteArrayOf(4, 5),
                    "check.mp3" to byteArrayOf(6),
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
                ByteArrayInputStream(zip("../escape.wav" to byteArrayOf(1))),
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
            importer.importZip(ByteArrayInputStream(zip("/move.wav" to byteArrayOf(1))), root, "absolute")
        }
        assertFailsWith<IllegalArgumentException> {
            importer.importZip(ByteArrayInputStream(zip("folder/move.wav" to byteArrayOf(1))), root, "nested")
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
    fun duplicateEventAssignmentsAreRejectedEvenAcrossExtensionsAndCase() {
        assertFailsWith<IllegalArgumentException> {
            SoundPackImporter().importZip(
                ByteArrayInputStream(
                    zip(
                        "move.wav" to byteArrayOf(1),
                        "MOVE.ogg" to byteArrayOf(2),
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
        val importer = SoundPackImporter(maxEntryBytes = 4, maxTotalBytes = 6)

        assertFailsWith<IllegalArgumentException> {
            importer.importZip(
                ByteArrayInputStream(zip("move.wav" to ByteArray(5) { 1 })),
                root,
                "entry-too-large",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            importer.importZip(
                ByteArrayInputStream(
                    zip(
                        "move.wav" to ByteArray(4) { 1 },
                        "capture.wav" to ByteArray(3) { 2 },
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
            SoundPackImporter(maxEntryBytes = 2).importZip(
                ByteArrayInputStream(
                    zip(
                        "move.wav" to byteArrayOf(1),
                        "capture.wav" to byteArrayOf(1, 2, 3),
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
        val bytes = byteArrayOf(9, 8, 7, 6)
        val file = SoundPackImporter(maxEntryBytes = 8).importSingle(
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
    fun singleEventImportRejectsUnsupportedTypeAndOversizePayload() {
        val importer = SoundPackImporter(maxEntryBytes = 3)
        val root = tempRoot()

        assertFailsWith<IllegalArgumentException> {
            importer.importSingle(ByteArrayInputStream(byteArrayOf(1)), "sound.txt", root, SoundEvent.MOVE)
        }
        assertFailsWith<IllegalArgumentException> {
            importer.importSingle(ByteArrayInputStream(ByteArray(4)), "sound.ogg", root, SoundEvent.MOVE)
        }
    }
}
