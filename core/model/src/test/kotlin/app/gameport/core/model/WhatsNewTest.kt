package app.gameport.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    // The real release files, as the app ships them.
    private val releases = File("../../app/src/main/assets/releases").listFiles { file -> file.extension == "json" }!!.map { Release.parse(it.readText()) }
    private val news = WhatsNew(releases)

    @Test
    fun `a player coming from the version before gets what the new one brings`() {
        val summary = news.between(seen = 600, current = 700)
        assertEquals(listOf("achievements", "offline", "sign-in"), summary.items.map { it.id })
        assertTrue("Moss 2" in summary.games!!.tested)
    }

    @Test
    fun `a player coming from 0_7_0 gets only what 0_7_1 brings`() {
        val summary = news.between(seen = 700, current = 701)
        assertEquals(listOf("saves-sync", "downloads", "expansion-files"), summary.items.map { it.id })
        assertEquals(listOf("Escape Simulator"), summary.games!!.tested)
    }

    @Test
    fun `a player coming from 0_7_1 gets what 0_7_2 brings, and the games of the version before are not told again`() {
        val summary = news.between(seen = 701, current = 702)
        assertEquals(listOf("playing-elsewhere", "incompatible-games", "game-answers", "reports"), summary.items.map { it.id })
        assertNull(summary.games)
    }

    @Test
    fun `a player who already saw the version is told nothing more`() {
        assertTrue(news.between(seen = 702, current = 702).isEmpty)
        assertTrue(news.between(seen = 800, current = 702).isEmpty)
    }

    @Test
    fun `a version that is not out yet is not announced`() {
        assertTrue(news.between(seen = 500, current = 600).isEmpty)
    }

    @Test
    fun `several versions skipped are told together without repeats`() {
        val games = news.between(seen = 0, current = 900).games!!.tested
        assertEquals(games.distinct(), games)
        assertEquals(6, games.size)
    }

    @Test
    fun `a version without tested games has no games block`() {
        val bare = Release(version = "0.8.0", previous = "0.7.1", code = 800, window = WindowNotes(items = listOf(WindowItem("a", "info", mapOf("en" to "A")))))
        val summary = WhatsNew(listOf(bare)).between(seen = 0, current = 800)
        assertNull(summary.games)
        assertEquals(1, summary.items.size)
    }

    @Test
    fun `the latest version is the newest release file, named like a version`() {
        val newest = releases.maxBy { it.code }
        assertEquals(newest.version, news.latestName)
        val (major, minor, patch) = newest.version.split(".").map { it.toInt() }
        assertEquals(major * 10000 + minor * 100 + patch, newest.code)
    }

    @Test
    fun `the preview shows only the newest version`() {
        val newest = releases.maxBy { it.code }
        val before = releases.first { it.version == newest.previous }
        assertEquals(news.between(seen = before.code, current = newest.code).items, news.latest().items)
    }

    @Test
    fun `a text falls back on English when its language is not translated`() {
        val text = mapOf("en" to "Hello", "fr" to "Bonjour")
        assertEquals("Bonjour", text.pick("fr"))
        assertEquals("Hello", text.pick("es"))
    }

    @Test
    fun `every release file is complete in every language`() {
        val problems = releases.flatMap { it.problems(listOf("en", "fr")) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `a release file with a missing language, an unknown icon and a headset is refused`() {
        val bad = Release(
            version = "0.8.0", previous = "0.7.1", code = 800,
            window = WindowNotes(items = listOf(WindowItem("a", "banana", mapOf("en" to "On the headset; here")))),
        )
        val problems = bad.problems(listOf("en", "fr")).joinToString("\n")
        assertTrue("no text in fr" in problems)
        assertTrue("unknown icon banana" in problems)
        assertTrue("headset" in problems)
        assertTrue("semicolon" in problems)
    }
}
