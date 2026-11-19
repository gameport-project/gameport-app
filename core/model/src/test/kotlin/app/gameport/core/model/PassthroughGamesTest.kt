package app.gameport.core.model

import app.gameport.core.model.vr.MetaQuestPlatform
import app.gameport.core.model.vr.PicoPlatform
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
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, other, told = told))
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, cubism, told = told))
        assertFalse(PassthroughGames.declared(PassthroughMode.AUTO, 3748900, told = told))
        assertTrue(PassthroughGames.wanted(other, told = told))
        assertFalse(PassthroughGames.wanted(other))
    }

    @Test
    fun `the player's choice wins over what the console says`() {
        val told = setOf(other, cubism)
        assertFalse(PassthroughGames.declared(PassthroughMode.OFF, other, told = told))
        assertFalse(PassthroughGames.declared(PassthroughMode.OFF, cubism, told = told))
        assertTrue(PassthroughGames.declared(PassthroughMode.ON, 3748900, told = told))
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

    @Test
    fun `the game that dies on the declaration on one headset keeps it everywhere else`() {
        // Cubism: the declaration is what makes its passthrough work on a Quest, and it kills the game in the graphics driver on a Pico.
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, cubism, MetaQuestPlatform.id))
        assertFalse(PassthroughGames.declared(PassthroughMode.AUTO, cubism, PicoPlatform.id))
        // No headset known: the declaration is given, as it always was.
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, cubism))
        // The games the console names are untouched by it.
        assertTrue(PassthroughGames.declared(PassthroughMode.AUTO, other, PicoPlatform.id, setOf(other)))
        // The player can still ask for it there: the choice is theirs.
        assertTrue(PassthroughGames.declared(PassthroughMode.ON, cubism, PicoPlatform.id))
    }
}
