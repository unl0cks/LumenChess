package dev.lumenchess.engine.host

import android.os.ParcelFileDescriptor
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineCapabilities
import dev.lumenchess.engine.api.EngineMultiPvCapability
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Exact production identity and capabilities of the pinned Reckless 0.9.0 engine. */
object Reckless09Engine {
    const val ID = "reckless-0.9.0"
    const val VERSION = "0.9.0"
    const val SOURCE_TAG = "v0.9.0"
    const val SOURCE_COMMIT = "0e92358f5acd66e5ac77b1bf558202e47c515435"
    const val LICENSE_SPDX = "AGPL-3.0"
    const val NETWORK = "v54-5478683c.nnue"
    const val NETWORK_SHA256 = "5478683cb1bababde29ae8f29468a99846726548fc6a0ed54cac40ab6d38efbf"

    val capabilities = EngineCapabilities(
        variants = setOf(Variant.STANDARD, Variant.CHESS960),
        multiPv = EngineMultiPvCapability(maxLines = 256),
        supportsPonder = false,
        strength = null,
    )
}

/**
 * UCI adapter around the packaged Reckless shared library. Both pipe endpoints and the native
 * engine live inside the isolated engine-host process; no UCI text crosses Binder.
 */
internal class Reckless09UciBackend : UciBackend {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)
    private val nativeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "reckless09-native")
    }
    private val outputExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "reckless09-output")
    }
    private val ioLock = Any()

    @Volatile
    private var listener: UciBackend.Listener? = null

    @Volatile
    private var writer: BufferedWriter? = null

    override fun start(listener: UciBackend.Listener) {
        check(started.compareAndSet(false, true)) { "Reckless 0.9.0 backend may only be started once" }
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
            val status = Reckless09NativeBridge.run(nativeInputFd, nativeOutputFd)
            if (!closed.get() && status != 0) {
                reportFailure(IllegalStateException("Reckless 0.9.0 native loop exited with status $status"))
            }
        }
    }

    override fun send(command: String) {
        if (closed.get()) return
        try {
            synchronized(ioLock) {
                if (closed.get()) return
                val output = checkNotNull(writer) { "Reckless 0.9.0 backend has not started" }
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

internal object Reckless09NativeBridge {
    init {
        System.loadLibrary("lumen_reckless09")
    }

    external fun run(inputFd: Int, outputFd: Int): Int
}
