package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallStateTest {
    @Test
    fun `a game on its way to being installed is in progress`() {
        val inProgress = listOf(
            InstallState.Queued, InstallState.Downloading(0.4f), InstallState.Downloading(0.1f, 0L, true), InstallState.Patching, InstallState.Installing,
            InstallState.ChoosingVersion(emptyList()), InstallState.ChoosingDuplicate("a.b"),
        )
        assertEquals(inProgress.size, inProgress.count { it.inProgress })
    }

    @Test
    fun `a game cut short, failed, installed or not installed is not`() {
        val others = listOf(InstallState.NotInstalled, InstallState.Interrupted, InstallState.Installed("a.b"), InstallState.Failed(InstallError.Offline))
        assertEquals(0, others.count { it.inProgress })
    }
}
