package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraContentTest {
    private fun dlc(id: Int, name: String, owned: Boolean = true, bytes: Long = 100L) =
        DlcContent(id, name, owned, listOf(AndroidDepot(id + 1, id, bytes * 2, bytes, 1L)))

    @Test
    fun `only the extra content the account has is offered, sorted by name, with its sizes`() {
        val extra = ExtraContent.of(listOf(dlc(2, "Zebra", bytes = 30), dlc(1, "apple"), dlc(3, "Not mine", owned = false)), installed = emptySet(), removable = emptySet(), work = null)
        assertEquals(listOf("apple", "Zebra"), extra.items.map { it.name })
        assertEquals(30L, extra.items.last().downloadBytes)
        assertEquals(60L, extra.items.last().installBytes)
    }

    @Test
    fun `what is installed, and what can be taken off alone, follow the lists given`() {
        val extra = ExtraContent.of(listOf(dlc(1, "A"), dlc(2, "B"), dlc(3, "C")), installed = setOf(1, 2), removable = setOf(2), work = null)
        val byName = extra.items.associateBy { it.name }
        assertTrue(byName.getValue("A").installed && !byName.getValue("A").removable)
        assertTrue(byName.getValue("B").installed && byName.getValue("B").removable)
        assertFalse(byName.getValue("C").installed)
    }

    @Test
    fun `a game without extra content, or whose extra content has no depot, has nothing to show`() {
        assertTrue(ExtraContent.of(emptyList(), emptySet(), emptySet(), null).isEmpty)
        assertTrue(ExtraContent.of(listOf(DlcContent(1, "Empty", true, emptyList())), emptySet(), emptySet(), null).isEmpty)
    }
}
