package app.gameport.core.patch

import app.gameport.core.patch.patches.UnrealUnityPlayerStubPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnrealUnityPlayerStubPatchTest {
    private fun context(isVr: Boolean?, family: String?) = PatchContext(steamAppId = 1, steamId = 2, personaName = "test", isVr = isVr, deviceFamily = family)

    @Test
    fun `the stand-in is a dex that holds Unity's player class with its field`() {
        val dex = UnrealUnityPlayerStubPatch.STUB
        assertEquals("dex\n", String(dex, 0, 4, Charsets.ISO_8859_1))
        val text = String(dex, Charsets.ISO_8859_1)
        assertTrue(text.contains("Lcom/unity3d/player/UnityPlayer;"))
        assertTrue(text.contains("currentActivity"))
    }

    @Test
    fun `it is wanted only for an Unreal game on a Pico, never for a game with a Unity player`() {
        assertTrue(UnrealUnityPlayerStubPatch.wanted(context(true, "pico"), hasUnreal = true, hasUnity = false))
        assertTrue(UnrealUnityPlayerStubPatch.wanted(context(null, "pico"), hasUnreal = true, hasUnity = false))
        assertFalse(UnrealUnityPlayerStubPatch.wanted(context(true, "pico"), hasUnreal = true, hasUnity = true))
        assertFalse(UnrealUnityPlayerStubPatch.wanted(context(true, "pico"), hasUnreal = false, hasUnity = false))
        assertFalse(UnrealUnityPlayerStubPatch.wanted(context(true, "meta"), hasUnreal = true, hasUnity = false))
        assertFalse(UnrealUnityPlayerStubPatch.wanted(context(false, "pico"), hasUnreal = true, hasUnity = false))
        assertFalse(UnrealUnityPlayerStubPatch.wanted(context(true, null), hasUnreal = true, hasUnity = false))
    }
}
