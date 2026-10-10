package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsTest {
    private val defaults = PlayerDefaults(heightCm = 175)

    @Test
    fun `a game without its own height follows the defaults`() {
        val settings = GameSettings(seated = true)
        assertTrue(settings.followsDefaults)
        assertEquals(175, settings.effectiveHeightCm(defaults))
        assertEquals(163, settings.eyeHeightCm(defaults))
    }

    @Test
    fun `a game's own height is not changed by the defaults`() {
        val own = GameSettings(seated = true, heightCm = 160)
        assertFalse(own.followsDefaults)
        assertEquals(160, own.effectiveHeightCm(defaults))
        assertEquals(160, own.effectiveHeightCm(PlayerDefaults(heightCm = 190)))
    }

    @Test
    fun `using the global configuration is dropping the game's own height`() {
        val own = GameSettings(seated = true, heightCm = 160)
        val reset = own.copy(heightCm = null)
        assertEquals(175, reset.effectiveHeightCm(defaults))
        assertTrue(reset.seated)
    }

    @Test
    fun `seated mode is off until the player asks for it`() {
        assertFalse(GameSettings().seated)
    }

    @Test
    fun `following the recentering of the headset is left to GamePort until the player chooses`() {
        assertEquals(RecenterMode.AUTO, GameSettings().recenter)
    }
}
