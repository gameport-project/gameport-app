package app.gameport.core.model

import app.gameport.core.model.LibraryChoice.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryChoiceTest {
    private fun lib(path: String, stored: Long) = Library(path, stored * 2, stored)

    /** The sizes (kept in the APK) of the libraries of the Pico game that began it: Vacation Simulator. */
    private val vacation = listOf(
        lib("lib/arm64-v8a/libPICO_TOBAPI.so", 71_408), lib("lib/arm64-v8a/libpxrplatformloader.so", 55_422), lib("lib/arm64-v8a/libovrplatformloader.so", 210_452),
        lib("lib/arm64-v8a/libsteam_api.so", 138_573), lib("lib/arm64-v8a/libOVRLipSync.so", 6_921_634), lib("lib/arm64-v8a/libil2cpp.so", 27_599_868), lib("lib/arm64-v8a/libunity.so", 12_204_665),
        lib("lib/arm64-v8a/libopenxr_loader.so", 466_099), lib("lib/arm64-v8a/libUnityOpenXR.so", 477_405), lib("lib/arm64-v8a/libaudiopluginresonanceaudio.so", 967_598),
    )

    @Test
    fun `the small libraries are taken, the engine's are only named`() {
        val pick = LibraryChoice.pick(vacation)
        assertTrue(pick.included.any { it.path.endsWith("libPICO_TOBAPI.so") })
        assertTrue(pick.included.any { it.path.endsWith("libpxrplatformloader.so") })
        assertEquals(setOf("libOVRLipSync.so", "libil2cpp.so", "libunity.so"), pick.left.map { it.path.substringAfterLast('/') }.toSet())
        assertTrue(pick.bytes <= LibraryChoice.BUDGET_BYTES)
    }

    @Test
    fun `GamePort's own libraries are never part of the choice, the game's own steam library is`() {
        val patched = vacation + listOf(
            lib("lib/arm64-v8a/libXrApiLayer_gameport.so", 573_232), lib("lib/arm64-v8a/libsteamclient.so", 7_271_504), lib("lib/arm64-v8a/libopenxr_loader_meta.so", 20_000),
        )
        val own = LibraryChoice.ofDeviceAbi(patched).map { it.path.substringAfterLast('/') }
        assertTrue(own.none { it == "libXrApiLayer_gameport.so" || it == "libsteamclient.so" || it == "libopenxr_loader.so" || it == "libopenxr_loader_meta.so" })
        assertTrue("libsteam_api.so" in own)
        assertTrue(LibraryChoice.isGamePortOwn("lib/arm64-v8a/libsteamclient.so"))
    }

    @Test
    fun `the platform's libraries come first, and one that does not fit is left out without ending the choice`() {
        val libraries = listOf(lib("lib/arm64-v8a/libPICO_TOBAPI.so", 300), lib("lib/arm64-v8a/libOculusBig.so", 900), lib("lib/arm64-v8a/libother.so", 200), lib("lib/arm64-v8a/libzzz.so", 150))
        val pick = LibraryChoice.pick(libraries, budget = 700)
        // The platform ones go first (300 fits, 900 does not), then the small ones that still fit (150, then 200).
        assertEquals(listOf("lib/arm64-v8a/libPICO_TOBAPI.so", "lib/arm64-v8a/libother.so", "lib/arm64-v8a/libzzz.so"), pick.included.map { it.path })
        assertEquals(listOf("lib/arm64-v8a/libOculusBig.so"), pick.left.map { it.path })
    }

    @Test
    fun `nothing is taken beyond the budget`() {
        val pick = LibraryChoice.pick(listOf(lib("lib/arm64-v8a/a.so", 400), lib("lib/arm64-v8a/b.so", 500), lib("lib/arm64-v8a/c.so", 600)), budget = 950)
        assertEquals(listOf("lib/arm64-v8a/a.so", "lib/arm64-v8a/b.so"), pick.included.map { it.path })
        assertEquals(900, pick.bytes)
        assertEquals(listOf("lib/arm64-v8a/c.so"), pick.left.map { it.path })
    }

    @Test
    fun `a budget that holds everything leaves nothing out, and one that holds nothing takes nothing`() {
        assertTrue(LibraryChoice.pick(vacation, budget = Long.MAX_VALUE).left.isEmpty())
        val none = LibraryChoice.pick(vacation, budget = 10)
        assertTrue(none.included.isEmpty())
        assertEquals(vacation.size, none.left.size)
    }

    @Test
    fun `the lists come in the order of the paths, whatever the order of the sizes`() {
        val pick = LibraryChoice.pick(vacation, budget = 600_000)
        assertEquals(pick.included.map { it.path }.sorted(), pick.included.map { it.path })
        assertEquals(pick.left.map { it.path }.sorted(), pick.left.map { it.path })
    }

    @Test
    fun `only the libraries of the device's ABI are looked at, the 64-bit ones first`() {
        val both = vacation + lib("lib/armeabi-v7a/libold.so", 10) + lib("lib/arm64-v8a/readme.txt", 1)
        assertTrue(LibraryChoice.ofDeviceAbi(both).all { it.path.startsWith("lib/arm64-v8a/") && it.path.endsWith(".so") })
        assertEquals(listOf("lib/armeabi-v7a/libold.so"), LibraryChoice.ofDeviceAbi(listOf(lib("lib/armeabi-v7a/libold.so", 10))).map { it.path })
        assertTrue(LibraryChoice.ofDeviceAbi(listOf(lib("lib/x86_64/libx.so", 10), lib("assets/a.so", 5))).isEmpty())
    }

    @Test
    fun `a choice that came out too large is made again without its biggest library, until none is left`() {
        var pick: LibraryChoice.Pick? = LibraryChoice.pick(vacation, budget = 2_000_000)
        val first = pick!!.included.size
        pick = LibraryChoice.withoutBiggest(pick)
        assertEquals(first - 1, pick!!.included.size)
        // The biggest of the ones that were included (libUnityOpenXR) is the one that goes.
        assertTrue(pick.left.any { it.path.endsWith("libUnityOpenXR.so") })
        while (pick != null && pick.included.isNotEmpty()) pick = LibraryChoice.withoutBiggest(pick)
        assertNull(LibraryChoice.withoutBiggest(LibraryChoice.Pick(emptyList(), vacation)))
    }
}
