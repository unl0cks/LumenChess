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

    /**
     * "Restrained, not piercing", measured: most energy sits low (spectral centroid) and almost none
     * above 5 kHz, where small phone speakers turn harsh.
     */
    @Test
    fun cuesAreWarmRatherThanPiercing() {
        val root = Files.createTempDirectory("lumen-sound-spectrum").toFile()
        SoundEvent.entries.forEach { event ->
            val (centroid, highShare) = spectrumOf(pcmOf(BuiltInSoundAssets.ensure(root, event).readBytes()))
            assertTrue(centroid < 2_000.0, "$event centroid ${centroid.toInt()} Hz is too bright")
            assertTrue(highShare < 0.03, "$event has ${"%.3f".format(highShare)} of its energy above 5 kHz")
        }
    }

    @Test
    fun illegalMoveIsASofterDullerKnockAndLowTimeIsTwoTicks() {
        val root = Files.createTempDirectory("lumen-sound-new-cues").toFile()
        fun pcm(event: SoundEvent) = pcmOf(BuiltInSoundAssets.ensure(root, event).readBytes())
        fun energy(samples: ShortArray) = samples.sumOf { it.toDouble() * it } / samples.size
        val illegal = pcm(SoundEvent.ILLEGAL_MOVE)
        val move = pcm(SoundEvent.MOVE)
        assertTrue(energy(illegal) < energy(move), "an illegal drop must be quieter than a real move")
        assertTrue(spectrumOf(illegal).first < spectrumOf(move).first, "an illegal drop must be duller than a move")

        // Two distinct onsets ~120 ms apart: loud in the first and second 20 ms windows after each tick.
        val lowTime = pcm(SoundEvent.LOW_TIME)
        fun windowEnergy(startMillis: Int) = lowTime.copyOfRange(startMillis * 44, (startMillis + 20) * 44).sumOf { it.toDouble() * it }
        val gap = windowEnergy(80)
        assertTrue(windowEnergy(0) > gap * 4 && windowEnergy(120) > gap * 4, "low-time cue must be two separate ticks")
    }

    /** Spectral centroid (Hz) and share of energy above 5 kHz, from a plain radix-2 FFT. */
    private fun spectrumOf(pcm: ShortArray): Pair<Double, Double> {
        var size = 1
        while (size < pcm.size) size = size shl 1
        val re = DoubleArray(size) { if (it < pcm.size) pcm[it].toDouble() else 0.0 }
        val im = DoubleArray(size)
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var length = 2
        while (length <= size) {
            val angle = -2.0 * Math.PI / length
            for (start in 0 until size step length) {
                for (k in 0 until length / 2) {
                    val cos = kotlin.math.cos(angle * k)
                    val sin = kotlin.math.sin(angle * k)
                    val a = start + k
                    val b = a + length / 2
                    val tr = re[b] * cos - im[b] * sin
                    val ti = re[b] * sin + im[b] * cos
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
            }
            length = length shl 1
        }
        var total = 0.0
        var weighted = 0.0
        var high = 0.0
        for (bin in 1 until size / 2) {
            val hz = bin * 44_100.0 / size
            val power = re[bin] * re[bin] + im[bin] * im[bin]
            total += power
            weighted += power * hz
            if (hz > 5_000.0) high += power
        }
        return weighted / total to high / total
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
