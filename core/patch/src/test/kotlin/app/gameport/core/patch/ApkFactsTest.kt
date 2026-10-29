package app.gameport.core.patch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFactsTest {
    @Test
    fun `the entry points built on Google's GameActivity need Android 11`() {
        assertTrue(ApkFacts.needsAndroid11Entry("com.unity3d.player.UnityPlayerGameActivity"))
        assertTrue(ApkFacts.needsAndroid11Entry("com.google.androidgamesdk.GameActivity"))
    }

    /**
     * Unreal names its own activities GameActivity as well, and they have nothing to do with Google's: a game started through one runs on
     * Android 10 perfectly well. Matching the word rather than the class would condemn every Unreal game there is.
     */
    @Test
    fun `Unreal's own GameActivity is not Google's and is left alone`() {
        assertFalse(ApkFacts.needsAndroid11Entry("com.epicgames.unreal.GameActivity"))
        assertFalse(ApkFacts.needsAndroid11Entry("com.epicgames.ue4.GameActivity"))
    }

    @Test
    fun `the classic Unity entry point is left alone, and so is a manifest that could not be read`() {
        assertFalse(ApkFacts.needsAndroid11Entry("com.unity3d.player.UnityPlayerActivity"))
        assertFalse(ApkFacts.needsAndroid11Entry(null))
    }
}
