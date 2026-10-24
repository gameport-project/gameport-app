package app.gameport.core.install

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpansionSizeTest {
    private val root: File = createTempDirectory("storage").toFile()

    @After
    fun clean() {
        root.deleteRecursively()
    }

    private fun obb(packageName: String) = File(root, "Android/obb/$packageName")

    @Test
    fun `the files of the game's folder are added up`() {
        obb("pkg").apply { mkdirs() }
        File(obb("pkg"), "main.1.pkg.obb").writeBytes(ByteArray(1_000))
        File(obb("pkg"), "patch.1.pkg.obb").writeBytes(ByteArray(250))
        assertEquals(1_250L, ExpansionSize.of(root, "pkg", hasAllFilesAccess = false))
    }

    @Test
    fun `no folder but the one above can be listed means the game has no expansion`() {
        File(root, "Android/obb").mkdirs()
        assertEquals(0L, ExpansionSize.of(root, "pkg", hasAllFilesAccess = true))
    }

    @Test
    fun `no folder and no way to list the one above means unknown, not zero`() {
        // Android/obb does not exist here, as when the system hides it from the app: nothing can be listed.
        assertNull(ExpansionSize.of(root, "pkg", hasAllFilesAccess = true))
    }

    @Test
    fun `without access to all files the folder above does not count`() {
        File(root, "Android/obb").mkdirs()
        assertNull(ExpansionSize.of(root, "pkg", hasAllFilesAccess = false))
    }
}
