package app.gameport.core.patch

import app.gameport.core.patch.patches.MetaLoaderStubPatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaLoaderStubPatchTest {
    private fun context(family: String?, isVr: Boolean? = true) =
        PatchContext(steamAppId = 1, steamId = 2, personaName = "test", isVr = isVr, deviceFamily = family)

    @Test
    fun `the stub is replaced on a headset that is not a Quest`() {
        assertTrue(MetaLoaderStubPatch.wanted(context("pico")))
        assertTrue(MetaLoaderStubPatch.wanted(context("openxr")))
        assertTrue(MetaLoaderStubPatch.wanted(context("pico", isVr = null)))
    }

    @Test
    fun `a Quest, a flat game and an unknown device are left alone`() {
        assertFalse(MetaLoaderStubPatch.wanted(context("meta")))
        assertFalse(MetaLoaderStubPatch.wanted(context("pico", isVr = false)))
        assertFalse(MetaLoaderStubPatch.wanted(context(null)))
    }
}
