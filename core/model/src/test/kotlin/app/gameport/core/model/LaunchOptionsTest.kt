package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchOptionsTest {
    private val normal = LaunchOption("-windowed", "Normal mode", vr = false)
    private val vr = LaunchOption("vrmode", "VR mode", vr = true)
    private val options = listOf(normal, vr)

    @Test
    fun `an entry is for VR when its type says so or the developer calls it that`() {
        assertTrue(LaunchOptions.isVr("vr", null))
        assertTrue(LaunchOptions.isVr("OpenXR", null))
        assertTrue(LaunchOptions.isVr(null, "Quantum Void VR mode"))
        assertFalse(LaunchOptions.isVr("default", "Quantum Void normal mode"))
        assertFalse(LaunchOptions.isVr(null, "Overview mode"))
    }

    @Test
    fun `words that negate VR do not make an entry VR`() {
        assertFalse(LaunchOptions.isVr(null, "Play without VR"))
        assertFalse(LaunchOptions.isVr(null, "Non-VR mode"))
        assertFalse(LaunchOptions.isVr(null, "No VR"))
        assertFalse(LaunchOptions.isVr(null, "Flat screen (VR not needed)"))
        assertFalse(LaunchOptions.isVr(null, "Play in VR", arguments = "-novr"))
        assertFalse(LaunchOptions.isVr(null, null, arguments = "-nohmd"))
        assertFalse(LaunchOptions.isVr(null, "Jouer sans VR"))
        assertTrue(LaunchOptions.isVr(null, "Play in VR", arguments = "-vr"))
        assertTrue(LaunchOptions.isVr(null, "No tutorial, VR mode"))
    }

    @Test
    fun `a type Steam gives decides, whatever the words say`() {
        assertTrue(LaunchOptions.isVr("vr", "Non-VR mode"))
        assertFalse(LaunchOptions.isVr("default", "Play in VR"))
    }

    @Test
    fun `without a choice a VR game gets the options meant for VR`() {
        assertEquals(listOf("vrmode"), LaunchOptions.chosen(options, picked = null, isVr = true))
        assertEquals(listOf("vrmode"), LaunchOptions.chosen(options, picked = null, isVr = null))
    }

    @Test
    fun `without a choice a flat game gets nothing`() {
        assertEquals(emptyList<String>(), LaunchOptions.chosen(options, picked = null, isVr = false))
    }

    @Test
    fun `the player's choice wins, even an empty one, and keeps Steam's order`() {
        assertEquals(listOf("-windowed", "vrmode"), LaunchOptions.chosen(options, picked = setOf("vrmode", "-windowed"), isVr = true))
        assertEquals(emptyList<String>(), LaunchOptions.chosen(options, picked = emptySet(), isVr = true))
        assertEquals(listOf("-windowed"), LaunchOptions.chosen(options, picked = setOf("-windowed"), isVr = false))
    }

    @Test
    fun `a choice for an argument Steam no longer lists is dropped`() {
        assertEquals(emptyList<String>(), LaunchOptions.chosen(options, picked = setOf("gone"), isVr = true))
    }

    @Test
    fun `the extra holds the arguments side by side, or nothing`() {
        assertEquals("-windowed vrmode", LaunchOptions.unityExtra(listOf("-windowed", "vrmode")))
        assertNull(LaunchOptions.unityExtra(emptyList()))
    }

    /** What Steam lists for games of the library, as it lists them: the type VR also holds arguments that pick a runtime, a graphics API or a capture. */
    @Test
    fun `only a plain switch for VR, or an option of the Android start, is picked at first`() {
        fun vr(arguments: String, android: Boolean = false) = LaunchOption(arguments, null, vr = true, android = android)
        val suggested = listOf(vr("vrmode"), vr("--vr"), vr("-vr"), vr("-xr"), vr("es_vr", android = true))
        suggested.forEach { assertTrue(it.arguments, LaunchOptions.isSuggested(it)) }
        val offered = listOf(vr("forceSteam"), vr("-d3d11"), vr("-vr -mrc"), vr("-vr -dx11"), vr("-nosteamopenxr"), vr("-vr -windowed -ResX=1280 -ResY=720"))
        offered.forEach { assertFalse(it.arguments, LaunchOptions.isSuggested(it)) }
        assertEquals(emptySet<String>(), LaunchOptions.preselected(offered))
    }

    @Test
    fun `an entry is split into its flags, a value staying with its flag`() {
        assertEquals(listOf("-vr", "-dx11"), LaunchOptions.flags("-vr -dx11"))
        assertEquals(listOf("-vr", "-windowed", "-ResX=1280", "-ResY=720"), LaunchOptions.flags("-vr -windowed -ResX=1280 -ResY=720"))
        assertEquals(listOf("-game x", "-vr"), LaunchOptions.flags("-game x -vr"))
        assertEquals(listOf("vrmode"), LaunchOptions.flags("vrmode"))
        assertEquals(emptyList<String>(), LaunchOptions.flags("  "))
    }
}
