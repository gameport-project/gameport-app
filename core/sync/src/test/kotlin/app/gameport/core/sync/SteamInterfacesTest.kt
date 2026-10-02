package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamInterfacesTest {
    @Test
    fun `interface versions are found between binary data`() {
        val bytes = "\u0000\u0001SteamClient022\u0000junk\u0002SteamUser023\u0000STEAMHTMLSURFACE_INTERFACE_VERSION_005\u0000".toByteArray(Charsets.ISO_8859_1)
        assertEquals(listOf("STEAMHTMLSURFACE_INTERFACE_VERSION_005", "SteamClient022", "SteamUser023"), SteamInterfaces.find(bytes))
    }

    @Test
    fun `nothing is found when there is no version`() {
        assertEquals(emptyList<String>(), SteamInterfaces.find("Steam without digits".toByteArray()))
    }
}
