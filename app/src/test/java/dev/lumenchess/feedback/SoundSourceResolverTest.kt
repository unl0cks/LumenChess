package dev.lumenchess.feedback

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SoundSourceResolverTest {
    private fun tempRoot(): File = Files.createTempDirectory("lumen-sound-source-test").toFile()

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
        val custom = File(packRoot, "capture.ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

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
    fun individualOverrideWinsOverSelectedPack() {
        val root = tempRoot()
        val packRoot = File(root, "sound-packs/night-pack").apply { mkdirs() }
        File(packRoot, "move.ogg").writeBytes(byteArrayOf(1))
        val override = File(root, "overrides/move.wav").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }

        val resolved = SoundSourceResolver(root).resolve(SoundEvent.MOVE, "night-pack")

        assertEquals(ResolvedSoundSource.LocalFile(override.canonicalFile), resolved)
    }
}
