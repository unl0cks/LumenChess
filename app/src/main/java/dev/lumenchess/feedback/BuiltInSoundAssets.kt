package dev.lumenchess.feedback

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Deterministically synthesizes the project-owned built-in Lumen feedback cues.
 * No third-party recordings or samples are embedded or downloaded.
 *
 * Piece cues are modal "struck wood" hits (a few damped sinusoids plus a short low-passed noise
 * click); game and check cues add soft bell partials. Every cue is peak-normalised, so the recipe
 * tables below are the only thing to tune.
 */
object BuiltInSoundAssets {
    private const val SAMPLE_RATE = 44_100

    /** Bump when a recipe changes: generated files are cached on disk by name and would go stale. */
    private const val RECIPE = "v2"
    private const val STRIKE_TAIL_SAMPLES = SAMPLE_RATE * 30 / 100
    private const val ATTACK_SAMPLES = (SAMPLE_RATE * 0.0004).toInt()

    /** One damped partial: [a] is Hz for strikes and a ratio of the note for chimes. */
    private class Partial(val a: Double, val decay: Double, val amp: Double)

    private class Strike(
        val startMillis: Int,
        val gain: Double,
        val partials: List<Partial>,
        val clickMillis: Double,
        val clickGain: Double,
        val clickSmoothing: Double,
        val seed: Int,
    )

    private class Chime(
        val startMillis: Int,
        val gain: Double,
        val hz: Double,
        val decayScale: Double,
        val partials: List<Partial>,
    )

    private class Cue(
        val durationMillis: Int,
        val peak: Double,
        val fadeMillis: Int,
        val strikes: List<Strike> = emptyList(),
        val chimes: List<Chime> = emptyList(),
    )

    private val wood = listOf(Partial(220.0, 55.0, 1.00), Partial(365.0, 70.0, 0.55), Partial(610.0, 95.0, 0.35), Partial(1_250.0, 160.0, 0.16))
    private val heavy = listOf(Partial(170.0, 42.0, 1.00), Partial(300.0, 60.0, 0.60), Partial(520.0, 85.0, 0.45), Partial(980.0, 140.0, 0.25), Partial(1_900.0, 220.0, 0.10))
    private val king = listOf(Partial(200.0, 52.0, 1.00), Partial(340.0, 68.0, 0.55), Partial(575.0, 92.0, 0.34), Partial(1_150.0, 150.0, 0.15))
    private val rook = listOf(Partial(260.0, 58.0, 1.00), Partial(430.0, 74.0, 0.55), Partial(720.0, 100.0, 0.34), Partial(1_420.0, 165.0, 0.15))
    /** Heavily damped and low: a muffled knock that reads as "no" without being a buzzer. */
    private val muffled = listOf(Partial(150.0, 95.0, 1.00), Partial(245.0, 130.0, 0.45), Partial(410.0, 170.0, 0.18))
    /** A small, dry clock escapement tick. */
    private val tick = listOf(Partial(820.0, 95.0, 1.00), Partial(1_340.0, 150.0, 0.45), Partial(2_050.0, 220.0, 0.16))
    private val bell = listOf(Partial(1.0, 7.0, 1.0), Partial(2.76, 11.0, 0.25), Partial(5.40, 19.0, 0.06))
    private val ping = listOf(Partial(1.0, 14.0, 1.0), Partial(2.0, 24.0, 0.16))

    private val cues = mapOf(
        SoundEvent.MOVE to Cue(
            160, peak = 0.72, fadeMillis = 10,
            strikes = listOf(Strike(0, 1.0, wood, 3.0, 0.5, 0.35, 11)),
        ),
        SoundEvent.CAPTURE to Cue(
            220, peak = 0.85, fadeMillis = 10,
            strikes = listOf(
                Strike(0, 1.0, heavy, 5.0, 0.9, 0.42, 23),
                Strike(30, 0.45, wood, 3.0, 0.4, 0.35, 29),
            ),
        ),
        SoundEvent.CHECK to Cue(
            340, peak = 0.78, fadeMillis = 10,
            strikes = listOf(Strike(0, 0.85, wood, 3.0, 0.5, 0.35, 31)),
            chimes = listOf(Chime(60, 0.20, 1_568.0, 1.6, ping)),
        ),
        SoundEvent.CASTLE to Cue(
            300, peak = 0.78, fadeMillis = 10,
            strikes = listOf(
                Strike(0, 0.9, king, 3.0, 0.5, 0.35, 41),
                Strike(105, 0.85, rook, 3.0, 0.5, 0.35, 43),
            ),
        ),
        SoundEvent.PROMOTION to Cue(
            550, peak = 0.72, fadeMillis = 10,
            strikes = listOf(Strike(0, 0.8, wood, 3.0, 0.5, 0.35, 51)),
            chimes = listOf(
                Chime(80, 0.30, 988.0, 1.2, bell),
                Chime(170, 0.34, 1_319.0, 1.2, bell),
            ),
        ),
        SoundEvent.ILLEGAL_MOVE to Cue(
            150, peak = 0.52, fadeMillis = 12,
            strikes = listOf(Strike(0, 1.0, muffled, 2.5, 0.35, 0.22, 61)),
        ),
        SoundEvent.LOW_TIME to Cue(
            240, peak = 0.55, fadeMillis = 12,
            strikes = listOf(
                Strike(0, 1.0, tick, 1.5, 0.25, 0.30, 71),
                Strike(120, 0.85, tick, 1.5, 0.25, 0.30, 73),
            ),
        ),
        SoundEvent.GAME_START to Cue(
            600, peak = 0.42, fadeMillis = 30,
            chimes = listOf(
                Chime(0, 0.55, 659.0, 1.0, bell),
                Chime(140, 0.55, 880.0, 1.0, bell),
            ),
        ),
        SoundEvent.GAME_END to Cue(
            700, peak = 0.42, fadeMillis = 40,
            chimes = listOf(
                Chime(0, 0.55, 784.0, 1.0, bell),
                Chime(160, 0.55, 587.0, 1.0, bell),
            ),
        ),
    )

    fun ensure(root: File, event: SoundEvent): File {
        root.mkdirs()
        require(root.isDirectory) { "Built-in sound destination is not a directory" }
        // Files from earlier recipes were stored unversioned; drop them so they are never reused.
        File(root, "${event.fileStem}.wav").takeIf { it.isFile }?.delete()
        val output = File(root, "${event.fileStem}.$RECIPE.wav").canonicalFile
        require(output.parentFile == root.canonicalFile) { "Unsafe built-in sound path" }
        if (output.isFile && output.length() > 44L && SoundMediaValidator.isSupported(output)) return output

        val cue = requireNotNull(cues[event]) { "Missing built-in sound cue" }
        val temporary = File(root, ".${event.fileStem}.$RECIPE.wav.tmp").canonicalFile
        require(temporary.parentFile == root.canonicalFile) { "Unsafe built-in sound staging path" }
        temporary.writeBytes(wavBytes(cue))
        if (output.exists()) require(output.delete()) { "Could not replace built-in sound" }
        require(temporary.renameTo(output)) { "Could not publish built-in sound" }
        return output
    }

    private fun wavBytes(cue: Cue): ByteArray {
        val samples = render(cue)
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, value ->
            val sample = Math.rint(value * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[index * 2] = (sample and 0xFF).toByte()
            pcm[index * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
        }
        return wavFile(pcm)
    }

    private fun render(cue: Cue): DoubleArray {
        val count = SAMPLE_RATE * cue.durationMillis / 1_000
        val buffer = DoubleArray(count)
        cue.strikes.forEach { addStrike(buffer, it) }
        cue.chimes.forEach { addChime(buffer, it) }

        val peak = buffer.maxOf { abs(it) }
        if (peak > 0.0) {
            val scale = cue.peak / peak
            for (i in buffer.indices) buffer[i] *= scale
        }
        val fade = SAMPLE_RATE * cue.fadeMillis / 1_000
        for (k in 0 until fade) buffer[count - fade + k] *= 1.0 - k.toDouble() / (fade - 1)
        for (k in 0 until ATTACK_SAMPLES) buffer[k] *= k.toDouble() / (ATTACK_SAMPLES - 1)
        return buffer
    }

    private fun addStrike(buffer: DoubleArray, strike: Strike) {
        val start = SAMPLE_RATE * strike.startMillis / 1_000
        for (i in 0 until minOf(STRIKE_TAIL_SAMPLES, buffer.size - start)) {
            val time = i.toDouble() / SAMPLE_RATE
            var value = 0.0
            for (p in strike.partials) value += p.amp * sin(2.0 * PI * p.a * time) * exp(-p.decay * time)
            buffer[start + i] += strike.gain * value
        }
        val clickSamples = (SAMPLE_RATE * strike.clickMillis / 1_000.0).toInt()
        var noise = strike.seed
        var smoothed = 0.0
        for (i in 0 until minOf(clickSamples, buffer.size - start)) {
            val time = i.toDouble() / SAMPLE_RATE
            noise = noise xor (noise shl 13)
            noise = noise xor (noise ushr 17)
            noise = noise xor (noise shl 5)
            val raw = (noise.toLong() and 0xFFFF_FFFFL).toDouble() / 4_294_967_295.0 * 2.0 - 1.0
            smoothed += strike.clickSmoothing * (raw - smoothed)
            val envelope = exp(-time / (strike.clickMillis / 1_000.0 / 3.0))
            buffer[start + i] += strike.gain * strike.clickGain * smoothed * envelope
        }
    }

    private fun addChime(buffer: DoubleArray, chime: Chime) {
        val start = SAMPLE_RATE * chime.startMillis / 1_000
        for (i in 0 until buffer.size - start) {
            val time = i.toDouble() / SAMPLE_RATE
            var value = 0.0
            for (p in chime.partials) {
                value += p.amp * sin(2.0 * PI * chime.hz * p.a * time) * exp(-p.decay * chime.decayScale * time)
            }
            val attack = minOf(1.0, time / 0.004)
            buffer[start + i] += chime.gain * attack * value
        }
    }

    private fun wavFile(pcm: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream(44 + pcm.size)
        DataOutputStream(bytes).use { out ->
            out.writeBytes("RIFF")
            writeLittleEndianInt(out, 36 + pcm.size)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            writeLittleEndianInt(out, 16)
            writeLittleEndianShort(out, 1)
            writeLittleEndianShort(out, 1)
            writeLittleEndianInt(out, SAMPLE_RATE)
            writeLittleEndianInt(out, SAMPLE_RATE * 2)
            writeLittleEndianShort(out, 2)
            writeLittleEndianShort(out, 16)
            out.writeBytes("data")
            writeLittleEndianInt(out, pcm.size)
            out.write(pcm)
        }
        return bytes.toByteArray()
    }

    private fun writeLittleEndianInt(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xFF)
        out.writeByte((value ushr 8) and 0xFF)
        out.writeByte((value ushr 16) and 0xFF)
        out.writeByte((value ushr 24) and 0xFF)
    }

    private fun writeLittleEndianShort(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xFF)
        out.writeByte((value ushr 8) and 0xFF)
    }
}
