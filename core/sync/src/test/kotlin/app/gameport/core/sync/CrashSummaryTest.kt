package app.gameport.core.sync

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashSummaryTest {
    // A tiny protobuf writer: just enough to make a tombstone of the shape Android writes.
    private fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (v and 0x7f.inv().toLong() != 0L) { out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }
        out.write(v.toInt())
        return out.toByteArray()
    }
    private fun number(field: Int, value: Long) = varint((field.toLong() shl 3)) + varint(value)
    private fun bytes(field: Int, body: ByteArray) = varint((field.toLong() shl 3) or 2) + varint(body.size.toLong()) + body
    private fun text(field: Int, value: String) = bytes(field, value.toByteArray())

    private fun frame(relativePc: Long, function: String?, offset: Long, file: String) =
        number(1, relativePc) + number(2, relativePc + 1000) + (function?.let { text(4, it) } ?: ByteArray(0)) + number(5, offset) + text(6, file)

    private fun thread(id: Long, name: String, vararg frames: ByteArray) =
        bytes(16, number(1, id) + bytes(2, number(1, id) + text(2, name) + frames.fold(ByteArray(0)) { all, f -> all + bytes(4, f) }))

    private val tombstone =
        text(2, "oculus/eureka:14/release-keys") + text(4, "2026-10-12 12:14:53.25+0200") + number(5, 26785) + number(6, 26967) + text(9, "com.example.game") +
            bytes(10, number(1, 11) + text(2, "SIGSEGV") + number(3, 1) + text(4, "SEGV_MAPERR") + number(9, 0x60000001bL)) +
            bytes(15, text(1, "null pointer dereference")) +
            thread(26967, "RenderThread 3",
                frame(0x7abab5c, "FVulkanCommandBufferManager::SubmitActiveCmdBufferFromPresent(bool)", 120, "/data/app/~~x==/com.example.game-y==/lib/arm64/libUE4.so"),
                frame(0x7b05248, null, 0, "/data/app/~~x==/com.example.game-y==/lib/arm64/libgodot.so")) +
            thread(26785, "GameThread") + thread(26800, "RHIThread")

    @Test
    fun `the signal, the address and the cause are told`() {
        val text = CrashSummary.of(tombstone)!!
        assertTrue(text, text.contains("signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault address 0x60000001b"))
        assertTrue(text, text.contains("cause: null pointer dereference"))
        assertTrue(text, text.startsWith("Native crash of com.example.game at 2026-10-12 12:14:53.25+0200"))
    }

    @Test
    fun `the crashing thread is named with the top of its call stack, a library with its function and one without symbols with its offset`() {
        val text = CrashSummary.of(tombstone)!!
        assertTrue(text, text.contains("crashing thread: RenderThread 3 (tid 26967)"))
        assertTrue(text, text.contains("#00 libUE4.so (FVulkanCommandBufferManager::SubmitActiveCmdBufferFromPresent(bool)+120)"))
        assertTrue(text, text.contains("#01 libgodot.so+0x7b05248"))
    }

    @Test
    fun `no path of the device is in the summary, only the names of the libraries`() {
        val text = CrashSummary.of(tombstone)!!
        assertTrue(text, !text.contains("/data/app"))
        assertTrue(text, !text.contains("~~"))
    }

    @Test
    fun `the other threads are listed by name`() {
        assertTrue(CrashSummary.of(tombstone)!!.contains("other threads (2): GameThread, RHIThread"))
    }

    @Test
    fun `a long stack is cut and says how much was left out`() {
        val frames = (0 until 20).map { frame(it.toLong(), "f$it", 0, "/lib/libx.so") }.toTypedArray()
        val text = CrashSummary.of(number(6, 1) + bytes(10, number(1, 11) + text(2, "SIGSEGV")) + thread(1, "Main", *frames))!!
        assertTrue(text, text.contains("#13"))
        assertTrue(text, !text.contains("#14"))
        assertTrue(text, text.contains("… 6 more"))
    }

    @Test
    fun `what is not a tombstone gives nothing, and a cut one gives what could be read`() {
        assertNull(CrashSummary.of(ByteArray(0)))
        assertNull(CrashSummary.of("not a tombstone at all".toByteArray()))
        val cut = tombstone.copyOf(tombstone.size - 9)
        assertNotNull(CrashSummary.of(cut))
        assertEquals(true, CrashSummary.of(cut)!!.contains("SIGSEGV"))
    }
}
