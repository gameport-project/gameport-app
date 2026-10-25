package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgePolicyTest {
    @Test
    fun `only a yes opens adult games, no answer is a no`() {
        assertTrue(AgePolicy.canSeeAdult(true))
        assertFalse(AgePolicy.canSeeAdult(false))
        assertFalse(AgePolicy.canSeeAdult(null))
    }

    @Test
    fun `adult games leave the library and the catalog unless the player may see them`() {
        val adult = Game(1, "Adult", Ownership.OWNED, null, isAdult = true)
        val other = Game(2, "Other", Ownership.OWNED, null)
        val library = Library(listOf(adult, other), false)
        assertEquals(listOf(2), library.visibleTo(false).games.map { it.appId })
        assertEquals(listOf(1, 2), library.visibleTo(true).games.map { it.appId })

        val catalog = AndroidCatalog(apps = listOf(AndroidApp(1, "Adult", tags = listOf(6650)), AndroidApp(2, "Other")))
        assertEquals(listOf(2), catalog.visibleTo(false).apps.map { it.appid })
        assertEquals(listOf(1, 2), catalog.visibleTo(true).apps.map { it.appid })
    }
}
