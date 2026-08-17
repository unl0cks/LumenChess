package dev.lumenchess.feedback

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltInSoundAssetsTest {
    @Test
    fun generatedBuiltInSoundsAreDeterministicValidWavFiles() {
        val rootA = Files.createTempDirectory("lumen-sound-a").toFile()
        val rootB = Files.createTempDirectory("lumen-sound-b").toFile()

        SoundEvent.entries.forEach { event ->
            val first = BuiltInSoundAssets.ensure(rootA, event)
            val second = BuiltInSoundAssets.ensure(rootB, event)
            val bytes = first.readBytes()

            assertEquals("${event.fileStem}.wav", first.name)
            assertTrue(bytes.size in 4_000..20_000)
            assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
            assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
            assertContentEquals(bytes, second.readBytes())
        }
    }

    @Test
    fun ensureReusesExistingGeneratedFileWithoutRewritingIt() {
        val root = Files.createTempDirectory("lumen-sound-existing").toFile()
        val first = BuiltInSoundAssets.ensure(root, SoundEvent.MOVE)
        val modified = 1_234_567_890L
        assertTrue(first.setLastModified(modified))

        val second = BuiltInSoundAssets.ensure(root, SoundEvent.MOVE)

        assertEquals(first.canonicalFile, second.canonicalFile)
        assertEquals(modified, second.lastModified())
    }

    @Test
    fun ensureRegeneratesCorruptedBuiltInFile() {
        val root = Files.createTempDirectory("lumen-sound-corrupt").toFile()
        val first = BuiltInSoundAssets.ensure(root, SoundEvent.CHECK)
        first.writeBytes(ByteArray(100) { 0 })

        val repaired = BuiltInSoundAssets.ensure(root, SoundEvent.CHECK)
        val bytes = repaired.readBytes()

        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
    }
}
