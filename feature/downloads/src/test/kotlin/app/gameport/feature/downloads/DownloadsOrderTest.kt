package app.gameport.feature.downloads

import app.gameport.core.model.AppKind
import app.gameport.core.model.Game
import app.gameport.core.model.InstallError
import app.gameport.core.model.InstallState
import app.gameport.core.model.Ownership
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsOrderTest {
    private fun entry(id: Int, name: String, state: InstallState, installedAt: Long? = null) =
        DownloadEntry(id, Game(id, name, Ownership.OWNED, null, AppKind.GAME), state, installedAt)

    @Test
    fun `work in progress, then updates, then problems, then the rest`() {
        val entries = listOf(
            entry(1, "Alpha", InstallState.Installed("a")),
            entry(2, "Bravo", InstallState.Failed(InstallError.NoApk)),
            entry(3, "Charlie", InstallState.Installed("c")),
            entry(4, "Delta", InstallState.Downloading(0.5f)),
        )
        val sorted = entries.sortedWith(downloadsOrder(withUpdate = setOf(3)))
        assertEquals(listOf(4, 3, 2, 1), sorted.map { it.appId })
    }

    @Test
    fun `in a group the latest installed or updated comes first`() {
        val entries = listOf(
            entry(1, "Alpha", InstallState.Installed("a"), installedAt = 100),
            entry(2, "Bravo", InstallState.Installed("b"), installedAt = 300),
            entry(3, "Charlie", InstallState.Installed("c"), installedAt = 200),
        )
        assertEquals(listOf(2, 3, 1), entries.sortedWith(downloadsOrder(emptySet())).map { it.appId })
    }

    @Test
    fun `games with no date come last in their group, by name`() {
        val entries = listOf(
            entry(1, "Zulu", InstallState.Failed(InstallError.NoApk)),
            entry(2, "Alpha", InstallState.Failed(InstallError.NoApk)),
            entry(3, "Mike", InstallState.Interrupted, installedAt = 50),
        )
        assertEquals(listOf(3, 2, 1), entries.sortedWith(downloadsOrder(emptySet())).map { it.appId })
    }
}
