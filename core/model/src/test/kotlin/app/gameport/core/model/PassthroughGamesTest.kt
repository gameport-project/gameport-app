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
    fun `a game the console names declares the passthrough on automatic, besides the ones built in`() {
        val told = setOf(other)
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, other, told))
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, cubism, told))
        assertFalse(PassthroughGames.declared(PassthroughMode.AUTO, 3748900, told))
        assertTrue(PassthroughGames.wanted(other, told))
        assertFalse(PassthroughGames.wanted(other))
    }

    @Test
    fun `the player's choice wins over what the console says`() {
        val told = setOf(other, cubism)
        assertFalse(PassthroughGames.declared(PassthroughMode.OFF, other, told))
        assertFalse(PassthroughGames.declared(PassthroughMode.OFF, cubism, told))
        assertTrue(PassthroughGames.declared(PassthroughMode.ON, 3748900, told))
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
