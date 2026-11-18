package app.gameport.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStartsTest {
    @Test
    fun `the first word of a process is a launch`() = assertTrue(RunStarts.isNew(storedPid = 0, pid = 100, recorded = false))

    @Test
    fun `the same process saying it again is not a new launch, however many times the cloud sync begins`() {
        repeat(5) { assertFalse(RunStarts.isNew(storedPid = 100, pid = 100, recorded = true)) }
    }

    @Test
    fun `another process is a new launch`() = assertTrue(RunStarts.isNew(storedPid = 100, pid = 200, recorded = true))

    @Test
    fun `a launch that was closed since is a new one even for the same process number`() = assertTrue(RunStarts.isNew(storedPid = 100, pid = 100, recorded = false))

    @Test
    fun `a hook that does not tell its process counts each word as a launch, as before`() = assertTrue(RunStarts.isNew(storedPid = 100, pid = 0, recorded = true))
}
