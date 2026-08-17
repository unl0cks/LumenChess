package dev.lumenchess.feedback

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Deterministically synthesizes the project-owned built-in Lumen feedback cues.
 * No third-party recordings or samples are embedded or downloaded.
 */
object BuiltInSoundAssets {
    private const val SAMPLE_RATE = 44_100
    private const val MAX_AMPLITUDE = 0.78

    private data class Cue(
        val durationMillis: Int,
        val frequencies: DoubleArray,
        val weights: DoubleArray,
        val decay: Double,
    )

    private val cues = mapOf(
        SoundEvent.MOVE to Cue(90, doubleArrayOf(430.0, 690.0), doubleArrayOf(0.72, 0.28), 24.0),
        SoundEvent.CAPTURE to Cue(125, doubleArrayOf(330.0, 515.0, 825.0), doubleArrayOf(0.55, 0.30, 0.15), 18.0),
        SoundEvent.CHECK to Cue(150, doubleArrayOf(610.0, 915.0), doubleArrayOf(0.65, 0.35), 15.0),
        SoundEvent.CASTLE to Cue(165, doubleArrayOf(285.0, 430.0, 570.0), doubleArrayOf(0.48, 0.34, 0.18), 13.0),
        SoundEvent.PROMOTION to Cue(150, doubleArrayOf(520.0, 780.0, 1_040.0), doubleArrayOf(0.48, 0.32, 0.20), 11.0),
        SoundEvent.GAME_START to Cue(150, doubleArrayOf(392.0, 523.25, 659.25), doubleArrayOf(0.38, 0.34, 0.28), 10.0),
        SoundEvent.GAME_END to Cue(150, doubleArrayOf(523.25, 392.0, 293.66), doubleArrayOf(0.34, 0.35, 0.31), 9.0),
    )

    fun ensure(root: File, event: SoundEvent): File {
        root.mkdirs()
        require(root.isDirectory) { "Built-in sound destination is not a directory" }
        val output = File(root, "${event.fileStem}.wav").canonicalFile
        require(output.parentFile == root.canonicalFile) { "Unsafe built-in sound path" }
        if (output.isFile && output.length() > 44L && SoundMediaValidator.isSupported(output)) return output

        val cue = requireNotNull(cues[event]) { "Missing built-in sound cue" }
        val temporary = File(root, ".${event.fileStem}.wav.tmp").canonicalFile
        require(temporary.parentFile == root.canonicalFile) { "Unsafe built-in sound staging path" }
        temporary.writeBytes(wavBytes(cue))
        if (output.exists()) require(output.delete()) { "Could not replace built-in sound" }
        require(temporary.renameTo(output)) { "Could not publish built-in sound" }
        return output
    }

    private fun wavBytes(cue: Cue): ByteArray {
        val sampleCount = SAMPLE_RATE * cue.durationMillis / 1_000
        val pcm = ByteArray(sampleCount * 2)
        for (sampleIndex in 0 until sampleCount) {
            val time = sampleIndex.toDouble() / SAMPLE_RATE.toDouble()
            val progress = sampleIndex.toDouble() / sampleCount.toDouble()
            val attack = (progress / 0.06).coerceIn(0.0, 1.0)
            val fade = ((1.0 - progress) / 0.16).coerceIn(0.0, 1.0)
            val envelope = attack * fade * exp(-cue.decay * time)
            var value = 0.0
            cue.frequencies.indices.forEach { index ->
                value += sin(2.0 * PI * cue.frequencies[index] * time) * cue.weights[index]
            }
            val sample = (value * envelope * MAX_AMPLITUDE * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[sampleIndex * 2] = (sample and 0xFF).toByte()
            pcm[sampleIndex * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
        }
        return wavFile(pcm)
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
