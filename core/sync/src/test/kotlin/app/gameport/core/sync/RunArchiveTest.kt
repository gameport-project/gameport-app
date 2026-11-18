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
    fun `a new run discards the files of the run before that it did not send, and keeps the ones set apart`() {
        write("log-start.txt", "run one start")
        write("diag-stacks.txt", "stacks of run one")
        write("threads.txt", "threads of run one")
        write("performance.txt", "perf of run one")
        write("previous-run-blackbox.txt", "kept")
        val archived = names + ("performance.txt" to "previous-performance.txt")
        val discarded = { name: String -> name.startsWith("diag-") || name == "threads.txt" || name == "performance.txt" }
        RunArchive.noteRun(folder.root, 100, archived, discarded)
        RunArchive.noteRun(folder.root, 200, archived, discarded)
        assertFalse("the stacks of a run before must not be read as this run's", File(folder.root, "diag-stacks.txt").exists())
        assertFalse(File(folder.root, "threads.txt").exists())
        assertFalse(File(folder.root, "performance.txt").exists())
        assertEquals("perf of run one", File(folder.root, "previous-performance.txt").readText())
        assertEquals("kept", File(folder.root, "previous-run-blackbox.txt").readText())
    }

    @Test
    fun `the same run discards nothing`() {
        write("diag-stacks.txt", "stacks")
        RunArchive.noteRun(folder.root, 100, names) { it.startsWith("diag-") }
        RunArchive.noteRun(folder.root, 100, names) { it.startsWith("diag-") }
        assertTrue(File(folder.root, "diag-stacks.txt").exists())
    }

    @Test
    fun `a hook that does not tell its run keeps nothing apart`() {
        write("log-start.txt", "x")
        assertFalse(RunArchive.noteRun(folder.root, 0, names))
    }
}
