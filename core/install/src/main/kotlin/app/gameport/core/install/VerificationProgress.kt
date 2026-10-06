package app.gameport.core.install

import java.io.File

/** The bytes this process has read so far (`rchar` of /proc/self/io), or null when the system does not say. */
internal fun readBytesOfProcess(file: File = File("/proc/self/io")): Long? = runCatching { parseReadBytes(file.readText()) }.getOrNull()

/** The `rchar` line of /proc/<pid>/io. */
internal fun parseReadBytes(text: String): Long? =
    text.lineSequence().firstOrNull { it.startsWith("rchar:") }?.substringAfter(':')?.trim()?.toLongOrNull()

/**
 * How far the downloader has got in checking the files already on the device. It announces nothing during that check, but it reads each file
 * through to know what is missing: the share of the bytes to check that this process has read since the check began is the progress. It is
 * an estimate, never 100 % (that is when the downloading starts), and 0 when the system does not give the figure.
 */
internal class VerificationProgress(private val bytesToCheck: Long, private val readBytes: () -> Long? = ::readBytesOfProcess) {
    private val start = readBytes()

    fun fraction(): Float {
        if (bytesToCheck <= 0 || start == null) return 0f
        val read = (readBytes() ?: return 0f) - start
        return (read.toDouble() / bytesToCheck).toFloat().coerceIn(0f, MAX)
    }

    private companion object {
        const val MAX = 0.99f
    }
}
