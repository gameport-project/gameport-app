package app.gameport.core.install

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HandoffStagingTest {
    @get:Rule val folder = TemporaryFolder()

    private val pkg = "com.example.game"
    private val staging by lazy { HandoffStaging(folder.newFolder("handoff")) }

    private fun download(vararg files: String): File = folder.newFolder("download").also { root ->
        files.forEach { path -> File(root, path).apply { parentFile?.mkdirs(); writeText("data of $path") } }
    }

    @Test
    fun `the expansion files are moved out of the download and named for the installed version`() {
        val downloaded = download("app.apk", "obb/main.745.$pkg.obb")
        val problems = mutableListOf<String>()
        staging.stage(downloaded, pkg, skipped = "patched", code = 800L, problems = problems)
        assertTrue(problems.toString(), problems.isEmpty())
        assertEquals(listOf("main.800.$pkg.obb"), staging.staged(pkg).map { it.name })
        assertFalse(File(downloaded, "obb/main.745.$pkg.obb").exists())
        assertTrue(File(downloaded, "app.apk").exists())
    }

    @Test
    fun `a staged file is found by its name, and a name that is a path is refused`() {
        staging.stage(download("main.5.$pkg.obb"), pkg, "patched", null, mutableListOf())
        assertNotNull(staging.file(pkg, "main.5.$pkg.obb"))
        assertNull(staging.file(pkg, "../main.5.$pkg.obb"))
        assertNull(staging.file(pkg, "sub/main.5.$pkg.obb"))
        assertNull(staging.file(pkg, ".hidden"))
        assertNull(staging.file(pkg, ""))
        assertNull(staging.file(pkg, "missing.obb"))
    }

    @Test
    fun `a package name that is not one is never turned into a path`() {
        val problems = mutableListOf<String>()
        assertEquals(emptyList<String>(), staging.stage(download("main.5.x.obb"), "../escape", "patched", null, problems))
        assertEquals(emptyList<File>(), staging.staged("../escape"))
        assertNull(staging.file("../escape", "main.5.x.obb"))
    }

    @Test
    fun `clearing removes what waits for the game, and only that game`() {
        staging.stage(download("main.5.$pkg.obb"), pkg, "patched", null, mutableListOf())
        staging.clear(pkg)
        assertEquals(emptyList<File>(), staging.staged(pkg))
    }
}
