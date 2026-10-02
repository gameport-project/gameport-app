package app.gameport.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallErrorTest {
    @Test
    fun `what the player can fix at once is not worth a report`() {
        assertFalse(InstallError.NotEnoughSpace(10, 5).reportable)
        assertFalse(InstallError.NotSignedIn.reportable)
        assertFalse(InstallError.Offline.reportable)
        assertFalse(InstallError.AppUpdating.reportable)
    }

    @Test
    fun `the other failures can be reported`() {
        assertTrue(InstallError.NoApk.reportable)
        assertTrue(InstallError.UnreadableApk.reportable)
        assertTrue(InstallError.VersionConflict.reportable)
        assertTrue(InstallError.Other("boom").reportable)
    }
}
