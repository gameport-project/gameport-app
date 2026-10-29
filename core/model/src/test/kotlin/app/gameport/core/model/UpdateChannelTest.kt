package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelTest {
    private fun channel(name: String, updated: Long, same: Boolean = false) = UpdateChannel(name, null, updated, 1_000, same, false)

    @Test
    fun `the normal channel comes first, then the others from the most recent to the oldest`() {
        val ordered = UpdateChannels.ordered(listOf(channel("android_branch", 10), channel("beta_branch", 30), channel("public", 20), channel("qol_branch", 40)))

        assertEquals(listOf("public", "qol_branch", "beta_branch", "android_branch"), ordered.map { it.name })
    }

    @Test
    fun `channels of the same date are put in the order of their names`() {
        assertEquals(listOf("a", "b"), UpdateChannels.ordered(listOf(channel("b", 5), channel("a", 5))).map { it.name })
    }

    @Test
    fun `there is something to show only when a channel other than the normal one exists`() {
        assertFalse(UpdateChannels.hasOthers(emptyList()))
        assertFalse(UpdateChannels.hasOthers(listOf(channel("public", 1))))
        assertTrue(UpdateChannels.hasOthers(listOf(channel("public", 1), channel("beta", 2))))
    }

    @Test
    fun `a channel may be newer when its build differs from the normal one and its date is later`() {
        val normal = channel("public", 100)

        assertTrue(UpdateChannels.mayBeNewer(channel("beta", 200), normal))
        assertFalse(UpdateChannels.mayBeNewer(channel("beta", 200, same = true), normal))
        assertFalse(UpdateChannels.mayBeNewer(channel("beta", 50), normal))
        assertFalse(UpdateChannels.mayBeNewer(normal, normal))
        assertFalse(UpdateChannels.mayBeNewer(channel("beta", 200), null))
    }
}
