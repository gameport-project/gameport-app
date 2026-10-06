package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallStateTest {
    @Test
    fun `a game on its way to being installed is in progress`() {
        val inProgress = listOf(
            InstallState.Queued, InstallState.Downloading(0.4f), InstallState.Downloading(0.1f, 0L, true), InstallState.Patching, InstallState.Installing, InstallState.Finishing,
            InstallState.ChoosingVersion(emptyList()), InstallState.ChoosingDuplicate("a.b"),
        )
        assertEquals(inProgress.size, inProgress.count { it.inProgress })
    }

    @Test
    fun `a game cut short, failed, installed or not installed is not`() {
        val others = listOf(InstallState.NotInstalled, InstallState.Interrupted, InstallState.Installed("a.b"), InstallState.Failed(InstallError.Offline))
        assertEquals(0, others.count { it.inProgress })
    }

    @Test
    fun `each state of an install is in one step, in order`() {
        assertEquals(InstallStage.DOWNLOAD, InstallState.Queued.stage)
        assertEquals(InstallStage.DOWNLOAD, InstallState.Downloading(0.5f).stage)
        assertEquals(InstallStage.PATCH, InstallState.Patching.stage)
        assertEquals(InstallStage.INSTALL, InstallState.Installing.stage)
        assertEquals(InstallStage.FINISH, InstallState.Finishing.stage)
        assertEquals(null, InstallState.Installed("a.b").stage)
        assertEquals(null, InstallState.Interrupted.stage)
    }
}
