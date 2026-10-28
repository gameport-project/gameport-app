package app.gameport.core.install

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ObbStashTest {
    @get:Rule val folder = TemporaryFolder()

    private fun obb() = File(folder.root, "obb/pkg").apply { mkdirs() }
    private fun stash() = File(folder.root, "stash/pkg")

    @Test
    fun `the expansion files are kept aside and put back as they were, nested folders included`() {
        val obb = obb()
        File(obb, "main.1.pkg.obb").writeText("main")
        File(obb, "assets").mkdirs()
        File(obb, "assets/a.bundle").writeText("bundle")
        assertEquals(2, ObbStash.stash(obb, stash()))
        assertTrue(obb.list().isNullOrEmpty())
        // The update leaves the folder empty, or removes it.
        obb.deleteRecursively()
        assertEquals(2, ObbStash.restore(stash(), obb))
        assertEquals("main", File(obb, "main.1.pkg.obb").readText())
        assertEquals("bundle", File(obb, "assets/a.bundle").readText())
        assertFalse(stash().exists())
    }

    @Test
    fun `what the update kept is not replaced by the copy aside, which goes`() {
        val obb = obb()
        File(obb, "main.1.pkg.obb").writeText("old")
        ObbStash.stash(obb, stash())
        File(obb, "main.1.pkg.obb").writeText("new")
        assertEquals(0, ObbStash.restore(stash(), obb))
        assertEquals("new", File(obb, "main.1.pkg.obb").readText())
        assertFalse(stash().exists())
    }

    @Test
    fun `nothing to keep, nothing to put back`() {
        assertEquals(0, ObbStash.stash(obb(), stash()))
        assertFalse(stash().exists())
        assertEquals(0, ObbStash.restore(stash(), obb()))
    }

    @Test
    fun `a try left half done is finished by the next restore`() {
        val obb = obb()
        File(obb, "patch.2.pkg.obb").writeText("p")
        ObbStash.stash(obb, stash())
        // GamePort was stopped here: the stash is all that is left.
        assertTrue(stash().isDirectory)
        assertEquals(1, ObbStash.restore(stash(), obb))
        assertEquals("p", File(obb, "patch.2.pkg.obb").readText())
    }
}
