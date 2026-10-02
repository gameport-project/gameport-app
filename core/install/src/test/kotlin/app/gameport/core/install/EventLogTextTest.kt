package app.gameport.core.install

import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EventLogTextTest {
    private val log = """
        2026-09-20 10:00:00 app=1 old entry
        2026-09-30 10:00:00 app=2 failed: boom
            at a.b.C.d(C.kt:1)
            at a.b.C.e(C.kt:2)
        2026-10-02 09:00:00 app=1 recent entry
    """.trimIndent() + "\n"

    private fun millis(date: String) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(date)!!.time

    @Test
    fun `entries older than the cutoff go with their stack lines`() {
        val kept = EventLogText.purgeOlderThan(log, millis("2026-10-01 00:00:00"))
        assertEquals("2026-10-02 09:00:00 app=1 recent entry\n", kept)
    }

    @Test
    fun `an entry keeps its stack lines when it is kept`() {
        val kept = EventLogText.purgeOlderThan(log, millis("2026-09-25 00:00:00"))
        assertEquals(
            "2026-09-30 10:00:00 app=2 failed: boom\n    at a.b.C.d(C.kt:1)\n    at a.b.C.e(C.kt:2)\n2026-10-02 09:00:00 app=1 recent entry\n",
            kept,
        )
    }

    @Test
    fun `forgetting a game drops its entries and nothing else`() {
        assertEquals("2026-09-20 10:00:00 app=1 old entry\n2026-10-02 09:00:00 app=1 recent entry\n", EventLogText.forget(log, 2))
    }

    @Test
    fun `reading a game gives only its entries`() {
        assertEquals("2026-09-30 10:00:00 app=2 failed: boom\n    at a.b.C.d(C.kt:1)\n    at a.b.C.e(C.kt:2)\n", EventLogText.entriesOf(log, 2))
    }

    @Test
    fun `an empty log stays empty`() {
        assertEquals("", EventLogText.purgeOlderThan("", 0L))
    }
}
