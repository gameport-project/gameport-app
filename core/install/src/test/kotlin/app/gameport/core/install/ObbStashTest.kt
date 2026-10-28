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
        assertEquals(2, ObbStash.stash(obb, stash()).moved)
        assertTrue(obb.list().isNullOrEmpty())
        // The update leaves the folder empty, or removes it.
        obb.deleteRecursively()
        assertEquals(2, ObbStash.restore(stash(), obb).moved)
        assertEquals("main", File(obb, "main.1.pkg.obb").readText())
        assertEquals("bundle", File(obb, "assets/a.bundle").readText())
        assertFalse(stash().exists())
    }

    @Test
    fun `what is put back is open to the game, its folders too`() {
        val obb = obb()
        File(obb, "assets/deep").apply { mkdirs() }.also { File(it, "vr.bundle").writeText("vr") }
        File(obb, "main.1.pkg.obb").writeText("main")
        ObbStash.stash(obb, stash())
        // The stash is GamePort's own folder: its modes are the restrictive ones GamePort makes.
        stash().walkTopDown().forEach {
            java.nio.file.Files.setPosixFilePermissions(it.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString(if (it.isDirectory) "rwxrwx---" else "rw-rw----"))
        }

        ObbStash.restore(stash(), obb)

        val others = { f: File -> java.nio.file.Files.getPosixFilePermissions(f.toPath()).filter { p -> p.name.startsWith("OTHERS") }.map { p -> p.name }.toSet() }
        assertEquals(setOf("OTHERS_READ", "OTHERS_EXECUTE"), others(File(obb, "assets")))
        assertEquals(setOf("OTHERS_READ", "OTHERS_EXECUTE"), others(File(obb, "assets/deep")))
        assertTrue("OTHERS_READ" in others(File(obb, "assets/deep/vr.bundle")))
        assertTrue("OTHERS_READ" in others(File(obb, "main.1.pkg.obb")))
    }

    @Test
    fun `an identical copy in the game's folder lets the one kept aside go`() {
        val obb = obb()
        File(obb, "main.1.pkg.obb").writeText("same")
        ObbStash.stash(obb, stash())
        File(obb, "main.1.pkg.obb").writeText("same")
        val result = ObbStash.restore(stash(), obb)
        assertTrue(result.failures.isEmpty())
        assertEquals("same", File(obb, "main.1.pkg.obb").readText())
        assertFalse(stash().exists())
    }

    @Test
    fun `a different file in the game's folder never makes the copy kept aside go`() {
        val obb = obb()
        File(obb, "main.1.pkg.obb").writeText("the whole file")
        ObbStash.stash(obb, stash())
        File(obb, "main.1.pkg.obb").writeText("part")
        val result = ObbStash.restore(stash(), obb)
        assertEquals(1, result.failures.size)
        assertEquals("the whole file", File(stash(), "main.1.pkg.obb").readText())
        assertEquals("part", File(obb, "main.1.pkg.obb").readText())
    }

    @Test
    fun `two restores at once lose nothing`() {
        val obb = obb()
        repeat(6) { File(obb, "f$it.obb").writeBytes(ByteArray(200_000) { b -> b.toByte() }) }
        ObbStash.stash(obb, stash())
        obb.deleteRecursively()
        val threads = List(2) { Thread { ObbStash.restore(stash(), obb) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        repeat(6) { assertEquals(200_000L, File(obb, "f$it.obb").length()) }
        assertFalse(stash().exists())
    }

    @Test
    fun `nothing to keep, nothing to put back`() {
        assertEquals(0, ObbStash.stash(obb(), stash()).moved)
        assertFalse(stash().exists())
        assertEquals(0, ObbStash.restore(stash(), obb()).moved)
    }

    @Test
    fun `a try left half done is finished by the next restore`() {
        val obb = obb()
        File(obb, "patch.2.pkg.obb").writeText("p")
        ObbStash.stash(obb, stash())
        // GamePort was stopped here: the stash is all that is left.
        assertTrue(stash().isDirectory)
        assertEquals(1, ObbStash.restore(stash(), obb).moved)
        assertEquals("p", File(obb, "patch.2.pkg.obb").readText())
    }
}
