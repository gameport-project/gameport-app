package app.gameport.core.patch

import app.gameport.core.patch.patches.UnityMultiviewViewportsPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityMultiviewViewportsPatchTest {
    private fun context(isVr: Boolean?, family: String?) = PatchContext(steamAppId = 1, steamId = 2, personaName = "test", isVr = isVr, deviceFamily = family)

    private val config = "gfx-enable-gfx-jobs=1\ngfx-threading-mode=3\nxr-latelatching-enabled=1\nxr-mvpvv-enabled=2\nandroidStartInFullscreen=1\n"

    @Test
    fun `a setting that is on is turned off and the rest of the file is kept`() {
        assertEquals("gfx-enable-gfx-jobs=1\ngfx-threading-mode=3\nxr-latelatching-enabled=1\nxr-mvpvv-enabled=0\nandroidStartInFullscreen=1\n", UnityMultiviewViewportsPatch.rewrite(config))
    }

    @Test
    fun `a setting already off, or absent, leaves the file alone`() {
        assertNull(UnityMultiviewViewportsPatch.rewrite(config.replace("xr-mvpvv-enabled=2", "xr-mvpvv-enabled=0")))
        assertNull(UnityMultiviewViewportsPatch.rewrite("gfx-threading-mode=3\n"))
        assertNull(UnityMultiviewViewportsPatch.rewrite(""))
    }

    @Test
    fun `a line with another name is not taken for the setting, and windows line ends are kept`() {
        assertNull(UnityMultiviewViewportsPatch.rewrite("xr-mvpvv-enabled-other=2\nmy-xr-mvpvv-enabled=2\n"))
        assertEquals("a=1\r\nxr-mvpvv-enabled=0\r\nb=2\r\n", UnityMultiviewViewportsPatch.rewrite("a=1\r\nxr-mvpvv-enabled=1\r\nb=2\r\n"))
    }

    @Test
    fun `patching twice gives the same file`() {
        val once = UnityMultiviewViewportsPatch.rewrite(config)!!
        assertNull(UnityMultiviewViewportsPatch.rewrite(once))
    }

    @Test
    fun `it is wanted only on a Pico, for a game that is not flat`() {
        assertTrue(UnityMultiviewViewportsPatch.wanted(context(isVr = true, family = "pico")))
        assertTrue(UnityMultiviewViewportsPatch.wanted(context(isVr = null, family = "pico")))
        assertFalse(UnityMultiviewViewportsPatch.wanted(context(isVr = true, family = "meta")))
        assertFalse(UnityMultiviewViewportsPatch.wanted(context(isVr = true, family = "openxr")))
        assertFalse(UnityMultiviewViewportsPatch.wanted(context(isVr = true, family = null)))
        assertFalse(UnityMultiviewViewportsPatch.wanted(context(isVr = false, family = "pico")))
    }
}
