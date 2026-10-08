package app.gameport.core.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunArchiveTest {
    @get:Rule val folder = TemporaryFolder()

    private val names = mapOf("log-start.txt" to "previous-log-start.txt", "hook.log" to "previous-hook.log")

    private fun write(name: String, text: String) = File(folder.root, name).writeText(text)

    @Test
    fun `the same run, or the first one, keeps nothing apart`() {
        write("log-start.txt", "first")
        assertFalse(RunArchive.noteRun(folder.root, 100, names))
        assertFalse(RunArchive.noteRun(folder.root, 100, names))
        assertFalse(File(folder.root, "previous-log-start.txt").exists())
    }

    @Test
    fun `a new run keeps what the one before left, before it is written over`() {
        write("log-start.txt", "run one start")
        write("hook.log", "run one tail")
        RunArchive.noteRun(folder.root, 100, names)
        assertTrue(RunArchive.noteRun(folder.root, 200, names))
        assertEquals("run one start", File(folder.root, "previous-log-start.txt").readText())
        assertEquals("run one tail", File(folder.root, "previous-hook.log").readText())
        // The third run keeps the second, not the first.
        write("log-start.txt", "run two start")
        assertTrue(RunArchive.noteRun(folder.root, 300, names))
        assertEquals("run two start", File(folder.root, "previous-log-start.txt").readText())
    }

    @Test
    fun `a hook that does not tell its run keeps nothing apart`() {
        write("log-start.txt", "x")
        assertFalse(RunArchive.noteRun(folder.root, 0, names))
    }
}
