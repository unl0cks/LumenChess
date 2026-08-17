package dev.lumenchess.feedback

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SoundSourceResolverTest {
    private fun tempRoot(): File = Files.createTempDirectory("lumen-sound-source-test").toFile()
    private fun wavHeader(): ByteArray = "RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray()
    private fun oggHeader(): ByteArray = "OggS".toByteArray()

    @Test
    fun builtInPackFallsBackToBundledSound() {
        val root = tempRoot()
        val resolved = SoundSourceResolver(root).resolve(SoundEvent.MOVE, "lumen-built-in")

        assertEquals(ResolvedSoundSource.BuiltIn(SoundEvent.MOVE), resolved)
    }

    @Test
    fun selectedCustomPackOverridesBuiltInWhenEventExists() {
        val root = tempRoot()
        val packRoot = File(root, "sound-packs/night-pack").apply { mkdirs() }
        val custom = File(packRoot, "capture.ogg").apply { writeBytes(oggHeader()) }

        val resolved = SoundSourceResolver(root).resolve(SoundEvent.CAPTURE, "night-pack")

        assertEquals(ResolvedSoundSource.LocalFile(custom.canonicalFile), resolved)
    }

    @Test
    fun missingCustomEventFallsBackToBuiltIn() {
        val root = tempRoot()
        File(root, "sound-packs/night-pack").mkdirs()

        val resolved = SoundSourceResolver(root).resolve(SoundEvent.CHECK, "night-pack")

        assertEquals(ResolvedSoundSource.BuiltIn(SoundEvent.CHECK), resolved)
    }

    @Test
    fun deletedCustomEventFallsBackToBuiltIn() {
        val root = tempRoot()
        val custom = File(root, "sound-packs/night-pack/check.ogg").apply {
            parentFile?.mkdirs()
            writeBytes(oggHeader())
        }
        check(custom.delete())

        val resolved = SoundSourceResolver(root).resolve(SoundEvent.CHECK, "night-pack")

        assertEquals(ResolvedSoundSource.BuiltIn(SoundEvent.CHECK), resolved)
    }

    @Test
    fun invalidOverrideFallsThroughToValidSelectedPack() {
        val root = tempRoot()
        File(root, "overrides/move.wav").apply {
            parentFile?.mkdirs()
            writeBytes("not-a-wav".toByteArray())
        }
        val pack = File(root, "sound-packs/night-pack/move.ogg").apply {
            parentFile?.mkdirs()
            writeBytes(oggHeader())
        }

        val resolved = SoundSourceResolver(root).resolve(SoundEvent.MOVE, "night-pack")

        assertEquals(ResolvedSoundSource.LocalFile(pack.canonicalFile), resolved)
    }

    @Test
    fun invalidSelectedPackEventFallsBackToBuiltIn() {
        val root = tempRoot()
        File(root, "sound-packs/night-pack/capture.ogg").apply {
            parentFile?.mkdirs()
            writeBytes("corrupt".toByteArray())
        }

        val resolved = SoundSourceResolver(root).resolve(SoundEvent.CAPTURE, "night-pack")

        assertEquals(ResolvedSoundSource.BuiltIn(SoundEvent.CAPTURE), resolved)
    }

    @Test
    fun individualOverrideWinsOverSelectedPack() {
        val root = tempRoot()
        val packRoot = File(root, "sound-packs/night-pack").apply { mkdirs() }
        File(packRoot, "move.ogg").writeBytes(oggHeader())
        val override = File(root, "overrides/move.wav").apply {
            parentFile?.mkdirs()
            writeBytes(wavHeader())
        }

        val resolved = SoundSourceResolver(root).resolve(SoundEvent.MOVE, "night-pack")

        assertEquals(ResolvedSoundSource.LocalFile(override.canonicalFile), resolved)
    }
}
