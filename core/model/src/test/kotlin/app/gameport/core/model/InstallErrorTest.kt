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
        assertTrue(InstallError.Blocked("policy").reportable)
    }

    @Test
    fun `a permission to give and a confirmation to answer are the player's to settle, not a report`() {
        assertFalse(InstallError.InstallPermission.reportable)
        assertFalse(InstallError.NotConfirmed.reportable)
    }

    @Test
    fun `the failures that wait on the player are the ones with something for them to do`() {
        assertTrue(InstallError.InstallPermission.needsPlayer)
        assertTrue(InstallError.NotConfirmed.needsPlayer)
        assertTrue(InstallError.ExpansionStoragePermission.needsPlayer)
    }

    @Test
    fun `a failure of its own does not wait on the player, so it is never started again by itself`() {
        assertFalse(InstallError.NoApk.needsPlayer)
        assertFalse(InstallError.UnreadableApk.needsPlayer)
        assertFalse(InstallError.Blocked(null).needsPlayer)
        assertFalse(InstallError.Other("boom").needsPlayer)
    }

    /** Every failure is either worth a report or has something for the player to do: one that is neither would sit there with no way on. */
    @Test
    fun `a failure always leads somewhere`() {
        val all = listOf(
            InstallError.NotEnoughSpace(10, 5), InstallError.NotSignedIn, InstallError.Offline, InstallError.AppUpdating,
            InstallError.NoApk, InstallError.UnreadableApk, InstallError.VersionConflict, InstallError.InstallPermission,
            InstallError.NotConfirmed, InstallError.Blocked(null), InstallError.ExpansionStoragePermission,
            InstallError.ExpansionFiles, InstallError.Other("boom"),
        )
        // Space, sign-in, connection and GamePort updating itself say in their own message what to do about them.
        val saysItself = listOf(InstallError.NotEnoughSpace(10, 5), InstallError.NotSignedIn, InstallError.Offline, InstallError.AppUpdating)
        all.filterNot { it in saysItself }.forEach { assertTrue("$it leads nowhere", it.reportable || it.needsPlayer) }
    }
}
