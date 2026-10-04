package app.gameport.core.patch

import app.gameport.core.patch.patches.SteamShimPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SteamShimConfigTest {
    private fun context(owned: List<Int>? = null, missing: List<Int> = emptyList(), family: Boolean = false) =
        PatchContext(steamAppId = 42, steamId = 7L, personaName = "Someone\nElse", ownedDlc = owned, missingDlc = missing, familyShared = family)

    @Test
    fun `without a library answer the config says nothing about DLC, so the shim keeps saying yes`() {
        assertEquals("appid=42\nsteamid=7\nname=Someone Else\n", SteamShimPatch.configFor(context()))
    }

    @Test
    fun `the DLC the account has and the ones it does not are listed once, in order`() {
        val config = SteamShimPatch.configFor(context(owned = listOf(30, 10, 30), missing = listOf(20)))
        assertEquals("appid=42\nsteamid=7\nname=Someone Else\ndlc=10,30\ndlcmissing=20\n", config)
    }

    @Test
    fun `a game with no DLC at all still says so`() {
        assertEquals("appid=42\nsteamid=7\nname=Someone Else\ndlc=\n", SteamShimPatch.configFor(context(owned = emptyList())))
    }

    @Test
    fun `Family Sharing is said only when the game is shared`() {
        assertEquals("appid=42\nsteamid=7\nname=Someone Else\nfamilysharing=1\n", SteamShimPatch.configFor(context(family = true)))
        assertFalse("familysharing" in SteamShimPatch.configFor(context()))
    }
}
