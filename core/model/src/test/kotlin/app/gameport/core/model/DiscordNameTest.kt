package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscordNameTest {
    @Test
    fun `a name is kept as typed, without the spaces around it`() {
        assertEquals("some.name", DiscordName.clean("  some.name  "))
    }

    @Test
    fun `the at sign in front and the characters that could be taken for markup go`() {
        assertEquals("some.name", DiscordName.clean("@@some.name"))
        assertEquals("Somebname", DiscordName.clean("Some<b>name"))
        assertEquals("name", DiscordName.clean(" @ name\n"))
    }

    @Test
    fun `what is left empty means no name`() {
        assertEquals("", DiscordName.clean(""))
        assertEquals("", DiscordName.clean(" @<> "))
    }

    @Test
    fun `a long name is cut`() {
        assertEquals("x".repeat(DiscordName.MAX_LENGTH), DiscordName.clean("x".repeat(100)))
    }
}
