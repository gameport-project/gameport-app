package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyChoiceTest {
    @Test
    fun `the own copy is used while nothing was picked`() {
        assertEquals(1L, FamilyChoice.resolve(listOf(2L, 1L), me = 1L, picked = null))
    }

    @Test
    fun `the copy picked is used while its owner still has the game`() {
        assertEquals(2L, FamilyChoice.resolve(listOf(1L, 2L), me = 1L, picked = 2L))
    }

    @Test
    fun `a pick that is no longer an owner falls back to the own copy, then to the first owner`() {
        assertEquals(1L, FamilyChoice.resolve(listOf(1L, 2L), me = 1L, picked = 3L))
        assertEquals(2L, FamilyChoice.resolve(listOf(2L, 3L), me = 1L, picked = 4L))
        assertEquals(null, FamilyChoice.resolve(emptyList(), me = 1L, picked = null))
    }

    @Test
    fun `a choice exists only when several accounts have the game`() {
        assertTrue(FamilyChoice.hasChoice(listOf(1L, 2L)))
        assertFalse(FamilyChoice.hasChoice(listOf(1L, 1L)))
        assertFalse(FamilyChoice.hasChoice(emptyList()))
    }
}
