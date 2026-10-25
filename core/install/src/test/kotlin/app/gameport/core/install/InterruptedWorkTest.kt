package app.gameport.core.install

import app.gameport.core.install.InterruptedWork.Resume
import app.gameport.core.model.InstallError
import app.gameport.core.model.InstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptedWorkTest {
    private fun resume(installed: Boolean = false, marker: Boolean = false, paused: Boolean = false, failed: Boolean = false, running: Boolean = false) =
        InterruptedWork.resume(installed, marker, paused, failed, running)

    @Test
    fun `a download that was cut short goes on`() = assertEquals(Resume.DOWNLOAD, resume())

    @Test
    fun `an update that was cut short goes on, as an update`() = assertEquals(Resume.UPDATE, resume(installed = true, marker = true))

    @Test
    fun `what is left next to an installed game without the mark of an update is left alone`() = assertEquals(Resume.NO, resume(installed = true))

    @Test
    fun `a paused or failed work does not go on, update or not`() {
        assertEquals(Resume.NO, resume(paused = true))
        assertEquals(Resume.NO, resume(failed = true))
        assertEquals(Resume.NO, resume(installed = true, marker = true, paused = true))
        assertEquals(Resume.NO, resume(installed = true, marker = true, failed = true))
    }

    @Test
    fun `a work that already runs is not started twice`() {
        assertEquals(Resume.NO, resume(running = true))
        assertEquals(Resume.NO, resume(installed = true, marker = true, running = true))
    }

    @Test
    fun `an update is going on while it downloads, patches or installs, or while it waits to go on again`() {
        assertTrue(InterruptedWork.updating(InstallState.Downloading(0.4f), hasUpdateMarker = true, paused = false, failed = false))
        assertTrue(InterruptedWork.updating(InstallState.Patching, true, false, false))
        assertTrue(InterruptedWork.updating(InstallState.Queued, true, false, false))
        // GamePort was closed: no operation yet, the mark is there, it goes on in a moment.
        assertTrue(InterruptedWork.updating(null, true, false, false))
    }

    @Test
    fun `an update that is paused, failed or over is not going on`() {
        assertFalse(InterruptedWork.updating(null, true, paused = true, failed = false))
        assertFalse(InterruptedWork.updating(null, true, paused = false, failed = true))
        assertFalse(InterruptedWork.updating(InstallState.Failed(InstallError.Offline), true, false, false))
        assertFalse(InterruptedWork.updating(InstallState.Installed("pkg"), true, false, false))
    }

    @Test
    fun `a download that is not an update never shows as one`() {
        assertFalse(InterruptedWork.updating(InstallState.Downloading(0.4f), hasUpdateMarker = false, paused = false, failed = false))
        assertFalse(InterruptedWork.updating(null, false, false, false))
    }
}
