package app.gameport.core.model.vr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
