package app.gameport.core.patch

import app.gameport.core.patch.patches.PicoDisguisePatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicoDisguisePatchTest {
    /** A fake badger that never calls android.graphics: it tags the icon's bytes so a test can tell a badged file from an untouched one. */
    private class FakeBadger(private val fails: Set<String> = emptySet()) : IconBadger {
        val calls = mutableListOf<Pair<Int, Float>>()
        override fun badge(icon: ByteArray, logo: ByteArray, fraction: Float): ByteArray? {
            calls += icon.size to fraction
            val name = String(icon)
            if (name in fails) return null
            return "badged:$name".toByteArray()
        }
    }

    private fun context(disguise: Boolean, isVr: Boolean?, family: String?) =
        PatchContext(steamAppId = 1, steamId = 2, personaName = "test", isVr = isVr, deviceFamily = family, picoStoreDisguise = disguise)

    @Test
    fun `wanted only when disguise is asked for, on a Pico, for a VR game`() {
        assertTrue(PicoDisguisePatch.wanted(context(disguise = true, isVr = true, family = "pico")))
        assertTrue(PicoDisguisePatch.wanted(context(disguise = true, isVr = null, family = "pico")))
        // The flag off: nothing, which is the default everywhere but the debug receiver.
        assertFalse(PicoDisguisePatch.wanted(context(disguise = false, isVr = true, family = "pico")))
        // A flat game, another headset, or no headset: nothing.
        assertFalse(PicoDisguisePatch.wanted(context(disguise = true, isVr = false, family = "pico")))
        assertFalse(PicoDisguisePatch.wanted(context(disguise = true, isVr = true, family = "meta")))
        assertFalse(PicoDisguisePatch.wanted(context(disguise = true, isVr = true, family = null)))
    }

    @Test
    fun `only the raster densities are badged, the adaptive xml is skipped`() {
        val paths = listOf(
            "res/mipmap-mdpi/ic_launcher.png",
            "res/mipmap-xxhdpi/ic_launcher.png",
            "res/mipmap-anydpi-v26/ic_launcher.xml",
        )
        assertEquals(
            listOf("res/mipmap-mdpi/ic_launcher.png", "res/mipmap-xxhdpi/ic_launcher.png"),
            PicoDisguisePatch.badgeablePaths(paths),
        )
    }

    @Test
    fun `every png density is badged with the logo at the set fraction`() {
        val badger = FakeBadger()
        val files = listOf(
            "res/mipmap-mdpi/ic_launcher.png" to "mdpi".toByteArray(),
            "res/mipmap-xxhdpi/ic_launcher.png" to "xxhdpi".toByteArray(),
            "res/mipmap-anydpi-v26/ic_launcher.xml" to "<xml>".toByteArray(),
        )
        val result = PicoDisguisePatch.badgeIcons(files, "logo".toByteArray(), badger)
        assertEquals(setOf("res/mipmap-mdpi/ic_launcher.png", "res/mipmap-xxhdpi/ic_launcher.png"), result.keys)
        assertEquals("badged:mdpi", String(result.getValue("res/mipmap-mdpi/ic_launcher.png")))
        assertEquals("badged:xxhdpi", String(result.getValue("res/mipmap-xxhdpi/ic_launcher.png")))
        // The xml was never handed to the badger.
        assertEquals(2, badger.calls.size)
        assertTrue(badger.calls.all { it.second == PicoDisguisePatch.BADGE_FRACTION })
    }

    @Test
    fun `an icon the badger cannot read is left untouched, not replaced by a broken file`() {
        val badger = FakeBadger(fails = setOf("broken"))
        val files = listOf(
            "res/mipmap-mdpi/ic_launcher.png" to "ok".toByteArray(),
            "res/mipmap-xxhdpi/ic_launcher.png" to "broken".toByteArray(),
        )
        val result = PicoDisguisePatch.badgeIcons(files, "logo".toByteArray(), badger)
        assertEquals(setOf("res/mipmap-mdpi/ic_launcher.png"), result.keys)
    }

    @Test
    fun `no icon files means nothing to rewrite`() {
        assertEquals(emptyMap<String, ByteArray>(), PicoDisguisePatch.badgeIcons(emptyList(), "logo".toByteArray(), FakeBadger()))
    }
}
