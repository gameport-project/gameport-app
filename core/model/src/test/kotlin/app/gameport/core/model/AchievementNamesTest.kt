package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementNamesTest {
    @Test
    fun `the prefix and the underscores go, each word starts with a capital`() {
        assertEquals("Ready To Rock", AchievementNames.readable("ach_ready_to_rock"))
        assertEquals("Good Luck", AchievementNames.readable("ACH_GOOD_LUCK"))
        assertEquals("First Blood", AchievementNames.readable("ACHIEVEMENT_FIRST_BLOOD"))
    }

    @Test
    fun `a name without prefix is kept, and an empty one stays as it is`() {
        assertEquals("Winner", AchievementNames.readable("winner"))
        assertEquals("ach_", AchievementNames.readable("ach_"))
    }
}
