package dev.lumenchess.engine.host

import android.os.ParcelFileDescriptor
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineCapabilities
import dev.lumenchess.engine.api.EngineMultiPvCapability
import dev.lumenchess.engine.api.EngineStrengthCapability
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Exact production identity and capabilities of the pinned Stockfish 18 engine. */
object Stockfish18Engine {
    const val ID = "stockfish-18"
    const val VERSION = "18"
    const val SOURCE_TAG = "sf_18"
    const val SOURCE_COMMIT = "cb3d4ee9b47d0c5aae855b12379378ea1439675c"
    const val LICENSE_SPDX = "GPL-3.0-or-later"

    val capabilities = EngineCapabilities(
        variants = setOf(Variant.STANDARD, Variant.CHESS960),
        multiPv = EngineMultiPvCapability(maxLines = 256),
        supportsPonder = true,
        strength = EngineStrengthCapability.EloRange(minElo = 1320, maxElo = 3190),
    )
}

/**
 * UCI adapter around the packaged Stockfish shared library. Both pipe endpoints and the native
 * engine live inside the isolated engine-host process; no UCI text crosses Binder.
 */
internal class Stockfish18UciBackend : UciBackend {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)
    private val nativeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "stockfish18-native")
    }
    private val outputExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "stockfish18-output")
    }
    private val ioLock = Any()

    @Volatile
    private var listener: UciBackend.Listener? = null

    @Volatile
    private var writer: BufferedWriter? = null

    override fun start(listener: UciBackend.Listener) {
        check(started.compareAndSet(false, true)) { "Stockfish 18 backend may only be started once" }
        this.listener = listener

        val inputPipe = ParcelFileDescriptor.createPipe()
        val outputPipe = ParcelFileDescriptor.createPipe()
        val nativeInputFd = inputPipe[0].detachFd()
        val nativeOutputFd = outputPipe[1].detachFd()
        val commandWriter = BufferedWriter(
            OutputStreamWriter(
                ParcelFileDescriptor.AutoCloseOutputStream(inputPipe[1]),
                StandardCharsets.UTF_8,
            ),
        )
        val outputReader = BufferedReader(
            InputStreamReader(
                ParcelFileDescriptor.AutoCloseInputStream(outputPipe[0]),
                StandardCharsets.UTF_8,
            ),
        )
        writer = commandWriter

        outputExecutor.execute {
            try {
                outputReader.use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        listener.onLine(line)
                    }
                }
            } catch (error: Throwable) {
                if (!closed.get()) reportFailure(error)
            }
        }

        nativeExecutor.execute {
            val status = Stockfish18NativeBridge.run(nativeInputFd, nativeOutputFd)
            if (!closed.get() && status != 0) {
                reportFailure(IllegalStateException("Stockfish 18 native loop exited with status $status"))
            }
        }
    }

    override fun send(command: String) {
        if (closed.get()) return
        try {
            synchronized(ioLock) {
                if (closed.get()) return
                val output = checkNotNull(writer) { "Stockfish 18 backend has not started" }
                output.write(command)
                output.newLine()
                output.flush()
            }
        } catch (error: Throwable) {
            if (!closed.get()) reportFailure(error)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(ioLock) {
            try {
                writer?.close()
            } catch (_: Throwable) {
                // Closing the command pipe is best-effort during isolated-host teardown.
            } finally {
                writer = null
            }
        }
        nativeExecutor.shutdown()
        outputExecutor.shutdown()
    }

    private fun reportFailure(error: Throwable) {
        if (failureReported.compareAndSet(false, true)) listener?.onFailure(error)
    }
}

internal object Stockfish18NativeBridge {
    init {
        System.loadLibrary("lumen_stockfish18")
    }

    external fun run(inputFd: Int, outputFd: Int): Int
}
