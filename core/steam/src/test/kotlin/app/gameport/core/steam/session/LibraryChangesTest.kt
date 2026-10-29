package app.gameport.core.steam.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryChangesTest {
    private val owned = setOf(10, 20, 30)

    private fun answer(vararg changed: Int, full: Boolean = false, number: Int = 500) = ChangesAnswer(number, changed.toSet(), full)

    @Test
    fun `only the apps of the account are read again, whatever else changed on Steam`() {
        val plan = planLibraryChanges(owned, answer(20, 999, 12345), lastNumber = 400)
        assertEquals(setOf(20), plan.forget)
        assertFalse(plan.forgetAll)
        assertTrue(plan.needsScan)
    }

    @Test
    fun `a game the account already had that got an Android build is among the changed ones`() {
        val plan = planLibraryChanges(owned, answer(30), lastNumber = 400)
        assertEquals(setOf(30), plan.forget)
    }

    @Test
    fun `nothing of the library changed means nothing to read and the number moves on`() {
        val plan = planLibraryChanges(owned, answer(999, number = 777), lastNumber = 400)
        assertTrue(plan.forget.isEmpty())
        assertFalse(plan.needsScan)
        assertEquals(777, plan.number)
    }

    @Test
    fun `without a number yet the library has just been read, so the number is only kept`() {
        val plan = planLibraryChanges(owned, answer(10, 20, number = 600), lastNumber = 0)
        assertTrue(plan.forget.isEmpty())
        assertFalse(plan.forgetAll)
        assertFalse(plan.needsScan)
        assertEquals(600, plan.number)
    }

    @Test
    fun `when Steam says it is too long ago to tell, everything is read again`() {
        val plan = planLibraryChanges(owned, answer(full = true, number = 900), lastNumber = 12)
        assertTrue(plan.forgetAll)
        assertTrue(plan.needsScan)
        assertEquals(900, plan.number)
    }
}
