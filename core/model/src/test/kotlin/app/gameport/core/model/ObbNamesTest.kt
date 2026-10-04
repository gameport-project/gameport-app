package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObbNamesTest {
    private val pkg = "com.vankrupt.pavlov"

    @Test
    fun `the main and the patch follow the version of the APK`() {
        assertEquals("main.2418.$pkg.obb", ObbNames.aligned("main.2398.$pkg.obb", pkg, 2418))
        assertEquals("patch.2418.$pkg.obb", ObbNames.aligned("patch.2398.$pkg.obb", pkg, 2418))
    }

    @Test
    fun `the overflow files follow it too`() {
        assertEquals("overflow1.2418.$pkg.obb", ObbNames.aligned("overflow1.2398.$pkg.obb", pkg, 2418))
        assertEquals("overflow2.2418.$pkg.obb", ObbNames.aligned("overflow2.2398.$pkg.obb", pkg, 2418))
        assertEquals("overflow12.2418.$pkg.obb", ObbNames.aligned("overflow12.2398.$pkg.obb", pkg, 2418))
    }

    @Test
    fun `a file that already has the version is left alone`() {
        assertNull(ObbNames.aligned("main.2418.$pkg.obb", pkg, 2418))
        assertNull(ObbNames.aligned("overflow1.2418.$pkg.obb", pkg, 2418))
    }

    @Test
    fun `what is not an expansion file of the game is left alone`() {
        assertNull(ObbNames.aligned("main.2398.com.other.game.obb", pkg, 2418))
        assertNull(ObbNames.aligned("main_assets_all.bundle", pkg, 2418))
        assertNull(ObbNames.aligned("overflow.2398.$pkg.obb", pkg, 2418))
        assertNull(ObbNames.aligned("extra1.2398.$pkg.obb", pkg, 2418))
    }

    @Test
    fun `a data file at the top of the depot goes to the top of the folder`() {
        assertEquals("main_assets_all.bundle", ObbNames.placement("main_assets_all.bundle", pkg))
    }

    @Test
    fun `the way to the folder is not repeated`() {
        assertEquals("main_assets_all.bundle", ObbNames.placement("obb/main_assets_all.bundle", pkg))
        assertEquals("main_assets_all.bundle", ObbNames.placement("Android/obb/main_assets_all.bundle", pkg))
        assertEquals("main_assets_all.bundle", ObbNames.placement("Android/obb/$pkg/main_assets_all.bundle", pkg))
        assertEquals("sub/data.bin", ObbNames.placement("obb/$pkg/sub/data.bin", pkg))
    }

    @Test
    fun `folders below are kept`() {
        assertEquals("aa/Android/x.bundle", ObbNames.placement("aa/Android/x.bundle", pkg))
    }

    @Test
    fun `an expansion file always goes at the top`() {
        assertEquals("main.416.$pkg.obb", ObbNames.placement("somewhere/deep/main.416.$pkg.obb", pkg))
    }

    @Test
    fun `an APK and what the downloader keeps are not placed`() {
        assertNull(ObbNames.placement("app.apk", pkg))
        assertNull(ObbNames.placement("sub/other.APK", pkg))
        assertNull(ObbNames.placement(".DepotDownloader/staging/file", pkg))
        assertNull(ObbNames.placement("", pkg))
        assertNull(ObbNames.placement("obb", pkg))
    }

    @Test
    fun `an older file with the same part is replaced by the new one`() {
        assertEquals(
            listOf("main.2428.$pkg.obb"),
            ObbNames.superseded(listOf("main.2428.$pkg.obb", "patch.2428.$pkg.obb"), listOf("main.2398.$pkg.obb"), pkg),
        )
        assertEquals(
            listOf("overflow1.2428.$pkg.obb"),
            ObbNames.superseded(listOf("overflow1.2428.$pkg.obb", "overflow2.2428.$pkg.obb"), listOf("overflow1.2398.$pkg.obb"), pkg),
        )
    }

    @Test
    fun `a file with the name of the new one, or of another part, or of another kind, is kept`() {
        assertEquals(emptyList<String>(), ObbNames.superseded(listOf("main.2398.$pkg.obb"), listOf("main.2398.$pkg.obb"), pkg))
        assertEquals(emptyList<String>(), ObbNames.superseded(listOf("patch.2428.$pkg.obb", "chunk.1.$pkg.obb", "data.bundle"), listOf("main.2398.$pkg.obb"), pkg))
        assertEquals(emptyList<String>(), ObbNames.superseded(listOf("main.2428.com.other.obb"), listOf("main.2398.$pkg.obb"), pkg))
        assertEquals(emptyList<String>(), ObbNames.superseded(listOf("main.2428.$pkg.obb"), listOf("chunk.1.$pkg.obb"), pkg))
    }

    @Test
    fun `a file is in an obb folder when the depot puts it there`() {
        assertEquals(true, ObbNames.inObbFolder("obb/assets/a.bundle", pkg))
        assertEquals(true, ObbNames.inObbFolder("Android/obb/$pkg/a.bundle", pkg))
        assertEquals(true, ObbNames.inObbFolder("x/$pkg/a.bundle", pkg))
        assertEquals(false, ObbNames.inObbFolder("res/layout.xml", pkg))
        assertEquals(false, ObbNames.inObbFolder("main_assets_all.bundle", pkg))
        assertEquals(false, ObbNames.inObbFolder("obb", pkg))
    }
}
