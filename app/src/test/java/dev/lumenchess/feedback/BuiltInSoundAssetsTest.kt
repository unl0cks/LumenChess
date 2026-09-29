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

            assertEquals("${event.fileStem}.v2.wav", first.name)
            assertTrue(bytes.size in 4_000..80_000, "unexpected size ${bytes.size} for $event")
            assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
            assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
            assertContentEquals(bytes, second.readBytes())
        }
    }

    @Test
    fun cuesNeverClipStartAndEndSilentlyAndCarryEnergy() {
        val root = Files.createTempDirectory("lumen-sound-shape").toFile()

        SoundEvent.entries.forEach { event ->
            val pcm = pcmOf(BuiltInSoundAssets.ensure(root, event).readBytes())
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            val rms = kotlin.math.sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)

            assertTrue(peak in 10_000..29_500, "$event peak $peak must be audible but never clip")
            assertTrue(rms in 1_500.0..6_000.0, "$event rms $rms must sit in the shared loudness band")
            assertTrue(kotlin.math.abs(pcm.first().toInt()) < 400, "$event must start near zero (no click)")
            assertEquals(0, pcm.last().toInt(), "$event must end on silence")
        }
    }

    @Test
    fun pieceCuesDifferAndEventsHaveDistinctCharacter() {
        val root = Files.createTempDirectory("lumen-sound-distinct").toFile()
        val byEvent = SoundEvent.entries.associateWith { pcmOf(BuiltInSoundAssets.ensure(root, it).readBytes()) }

        assertEquals(SoundEvent.entries.size, byEvent.values.map { it.toList() }.toSet().size)
        // A castle is two pieces landing (king, then rook), so it is longer than a plain move; a
        // capture is heavier (more energy) than a plain move.
        assertTrue(byEvent.getValue(SoundEvent.CASTLE).size > byEvent.getValue(SoundEvent.MOVE).size)
        fun energy(event: SoundEvent) = byEvent.getValue(event).sumOf { it.toDouble() * it }
        assertTrue(energy(SoundEvent.CAPTURE) > energy(SoundEvent.MOVE))
    }

    @Test
    fun staleUnversionedFilesFromEarlierRecipesAreRemoved() {
        val root = Files.createTempDirectory("lumen-sound-stale").toFile()
        val stale = java.io.File(root, "move.wav").apply { writeBytes(ByteArray(2_000)) }

        BuiltInSoundAssets.ensure(root, SoundEvent.MOVE)

        assertTrue(!stale.exists(), "the pre-v2 sine blip must not linger on disk")
    }

    private fun pcmOf(wav: ByteArray): ShortArray =
        ShortArray((wav.size - 44) / 2) { i ->
            ((wav[44 + i * 2].toInt() and 0xFF) or (wav[45 + i * 2].toInt() shl 8)).toShort()
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
