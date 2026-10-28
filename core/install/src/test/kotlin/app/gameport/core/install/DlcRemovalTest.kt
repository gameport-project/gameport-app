package app.gameport.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlcRemovalTest {
    private val pkg = "com.PineStudio.EscapeSimulator"

    @Test
    fun `an extra content is taken off by the files its depot lists, at the path they have on the device`() {
        val amongUs = listOf("obb/assets/amongus1.bundle" to 340964992L, "obb/assets/amongus1.bundle.manifest" to 5711L)
        val base = listOf("EscapeSimulator.apk", "obb/assets/AssetBundles", "obb/assets/room1.bundle")
        val plan = DlcRemoval.plan(amongUs, base, emptyList(), pkg)
        assertEquals(listOf("assets/amongus1.bundle", "assets/amongus1.bundle.manifest"), plan.map { it.relativePath })
        assertEquals(340964992L, plan.first().size)
    }

    @Test
    fun `a file that the game itself, or another extra content, also holds is never taken off`() {
        val dlc = listOf("obb/assets/shared.bundle" to 10L, "obb/assets/only.bundle" to 20L, "obb/assets/other.bundle" to 30L)
        val plan = DlcRemoval.plan(dlc, keptFiles = listOf("obb/assets/shared.bundle"), keptPlaced = listOf("assets/other.bundle"), packageName = pkg)
        assertEquals(listOf("assets/only.bundle"), plan.map { it.relativePath })
    }

    @Test
    fun `what GamePort never places, an APK or a hidden file, is not planned either`() {
        val plan = DlcRemoval.plan(listOf("Game.apk" to 1L, ".DepotDownloader/x.manifest" to 2L, "obb/main.1.$pkg.obb" to 3L), emptyList(), emptyList(), pkg)
        assertEquals(listOf("main.1.$pkg.obb"), plan.map { it.relativePath })
    }

    @Test
    fun `nothing is planned for an empty list`() {
        assertTrue(DlcRemoval.plan(emptyList(), listOf("a"), emptyList(), pkg).isEmpty())
    }
}
