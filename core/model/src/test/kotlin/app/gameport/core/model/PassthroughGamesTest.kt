package app.gameport.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassthroughGamesTest {
    private val cubism = 804530
    private val other = 1125240

    @Test
    fun `automatic gives the declaration to the listed games only`() {
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, cubism))
        assertFalse(PassthroughGames.declared(PassthroughMode.AUTO, other))
    }

    @Test
    fun `the player's choice wins over the list`() {
        assertFalse(PassthroughGames.declared(PassthroughMode.OFF, cubism))
        assertTrue(PassthroughGames.declared(PassthroughMode.ON, other))
    }

    @Test
    fun `a game is automatic until the player chooses`() {
        assertTrue(GameSettings().passthrough == PassthroughMode.AUTO)
    }
}
