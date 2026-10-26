package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymptomTest {
    @Test
    fun `symptoms are written by code in the order of the list`() {
        assertEquals("no_image,no_sound", Symptom.write(setOf(Symptom.NO_SOUND, Symptom.NO_IMAGE)))
        assertEquals("", Symptom.write(emptySet()))
    }

    @Test
    fun `codes read back, and a code this version does not know is left out`() {
        assertEquals(setOf(Symptom.NO_CONTROLS, Symptom.SLOW), Symptom.parse(listOf("slow", "no_controls", "something_new")))
    }

    @Test
    fun `every code is unique and written in lower case without a comma`() {
        val codes = Symptom.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it == it.lowercase() && !it.contains(',') })
    }
}
