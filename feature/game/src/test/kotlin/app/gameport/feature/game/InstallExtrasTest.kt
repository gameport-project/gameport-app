package app.gameport.feature.game

import app.gameport.core.model.ExtraPlanItem
import app.gameport.core.model.InstallStage
import app.gameport.core.model.InstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the card of an install says of each extra content, in every step of the install. */
class InstallExtrasTest {
    private val withGame = ExtraPlanItem(1, "In the game", withGame = true)
    private val first = ExtraPlanItem(2, "First", withGame = false)
    private val second = ExtraPlanItem(3, "Second", withGame = false)
    private val third = ExtraPlanItem(4, "Third", withGame = false)
    private val all = listOf(withGame, first, second, third)

    private fun stands(stage: InstallStage, index: Int?) = extraStands(all, stage, index).map { it.second }

    @Test
    fun `nothing chosen gives nothing to show`() {
        assertTrue(extraStands(emptyList(), InstallStage.DOWNLOAD, null).isEmpty())
    }

    @Test
    fun `while the game downloads, every extra waits`() {
        assertEquals(List(4) { Stand.WAITING }, stands(InstallStage.DOWNLOAD, null))
    }

    @Test
    fun `once the game is downloaded the extras in its download are in place, the others wait`() {
        val expected = listOf(Stand.DONE, Stand.WAITING, Stand.WAITING, Stand.WAITING)
        assertEquals(expected, stands(InstallStage.PATCH, null))
        assertEquals(expected, stands(InstallStage.INSTALL, null))
        assertEquals(expected, stands(InstallStage.FINISH, null))
    }

    @Test
    fun `the extras fetched on their own go one after the other`() {
        assertEquals(listOf(Stand.DONE, Stand.ACTIVE, Stand.WAITING, Stand.WAITING), stands(InstallStage.FINISH, 1))
        assertEquals(listOf(Stand.DONE, Stand.DONE, Stand.ACTIVE, Stand.WAITING), stands(InstallStage.FINISH, 2))
        assertEquals(listOf(Stand.DONE, Stand.DONE, Stand.DONE, Stand.ACTIVE), stands(InstallStage.FINISH, 3))
    }

    @Test
    fun `a place past the last one leaves every extra in place`() {
        assertEquals(List(4) { Stand.DONE }, stands(InstallStage.FINISH, 4))
    }

    @Test
    fun `the order of the list is kept`() {
        assertEquals(all, extraStands(all, InstallStage.FINISH, 2).map { it.first })
    }

    @Test
    fun `a patch of an installed game goes through the steps of an install`() {
        assertEquals(InstallState.Queued, Repatch.Running(RepatchStage.QUEUED).asInstallState())
        assertEquals(InstallState.Downloading(0.4f, 1_000L), Repatch.Running(RepatchStage.DOWNLOADING, 0.4f, 1_000L).asInstallState())
        assertEquals(InstallState.Downloading(0f, 0L), Repatch.Running(RepatchStage.DOWNLOADING).asInstallState())
        assertEquals(InstallState.Patching, Repatch.Running(RepatchStage.PATCHING).asInstallState())
        assertEquals(InstallState.Installing, Repatch.Running(RepatchStage.INSTALLING).asInstallState())
    }

    @Test
    fun `a game with extras only in its download has none fetched on their own`() {
        val onlyInGame = listOf(withGame, ExtraPlanItem(5, "Another", withGame = true))
        assertEquals(List(2) { Stand.WAITING }, extraStands(onlyInGame, InstallStage.DOWNLOAD, null).map { it.second })
        assertEquals(List(2) { Stand.DONE }, extraStands(onlyInGame, InstallStage.FINISH, null).map { it.second })
    }
}
