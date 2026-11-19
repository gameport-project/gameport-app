package app.gameport.core.patch

import app.gameport.core.patch.patches.XrLayerPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrLayerManifestTest {
    @Test
    fun `the manifest is the same as before wherever the extensions are not offered`() {
        val plain = XrLayerPatch.manifest(offersMetaControllers = false)
        assertFalse(plain.contains("instance_extensions"))
        assertTrue(plain.contains("XR_APILAYER_GAMEPORT_compat"))
    }

    @Test
    fun `a Pico game with Meta's plugin is offered the three extensions of Meta's controllers`() {
        val offered = XrLayerPatch.manifest(offersMetaControllers = true)
        XrLayerPatch.META_CONTROLLER_EXTENSIONS.forEach { assertTrue(offered.contains(it)) }
        assertEquals(1, Regex("instance_extensions").findAll(offered).count())
        // Still one JSON object the loader can read: braces and brackets are balanced.
        assertEquals(offered.count { it == '{' }, offered.count { it == '}' })
        assertEquals(offered.count { it == '[' }, offered.count { it == ']' })
    }
}
