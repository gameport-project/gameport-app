package app.gameport.core.model.vr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VrPlatformsTest {
    private fun device(manufacturer: String, brand: String = manufacturer, vararg features: String) =
        DeviceInfo(manufacturer, brand, "model", features.toSet())

    @Test
    fun `a quest is reported as meta even when it also has head tracking`() {
        val quest = device("Oculus", features = arrayOf("android.hardware.vr.headtracking"))
        assertEquals(MetaQuestPlatform, VrPlatforms.detect(quest))
    }

    @Test
    fun `a pico is recognised by its manufacturer`() {
        assertEquals(PicoPlatform, VrPlatforms.detect(device("PICO")))
    }

    @Test
    fun `an unknown headset with head tracking falls back to openxr`() {
        val other = device("SomeVendor", features = arrayOf("android.hardware.vr.headtracking"))
        assertEquals(OpenXrPlatform, VrPlatforms.detect(other))
    }

    @Test
    fun `a phone is not a headset`() {
        assertNull(VrPlatforms.detect(device("Google", features = arrayOf("android.hardware.camera"))))
    }

    @Test
    fun `patched games get every platform's launcher category once`() {
        val categories = VrPlatforms.allImmersiveLauncherCategories
        assertEquals(categories.distinct(), categories)
        assertEquals(
            setOf("com.oculus.intent.category.VR", "com.yvr.intent.category.VR", "org.khronos.openxr.intent.category.IMMERSIVE_HMD"),
            categories.toSet(),
        )
    }

    @Test
    fun `only the family whose store refuses a re-signed game is said to`() {
        assertTrue(VrPlatforms.refusesReSignedGames(PicoPlatform.id))
        assertFalse(VrPlatforms.refusesReSignedGames(MetaQuestPlatform.id))
        assertFalse(VrPlatforms.refusesReSignedGames(OpenXrPlatform.id))
        assertFalse(VrPlatforms.refusesReSignedGames(null))
        // A game that did not start is worth offering a name of GamePort's own on a Quest too, though nothing renames a game there by itself.
        assertTrue(VrPlatforms.renameMayHelp(PicoPlatform.id))
        assertTrue(VrPlatforms.renameMayHelp(MetaQuestPlatform.id))
        assertFalse(VrPlatforms.renameMayHelp(OpenXrPlatform.id))
        assertFalse(VrPlatforms.renameMayHelp(null))
    }
}
