package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {
    private fun local(vararg files: Pair<String, String>) =
        files.associate { (rel, sha) -> rel to LocalFile(rel, sha, 10, 1_000) }

    private fun cloud(vararg files: Pair<String, String>) =
        files.associate { (rel, sha) -> rel to CloudFile("%R%$rel", rel, sha, 10, 2_000) }

    private val baseline = Baseline(changeNumber = 5, files = mapOf("a" to "1"))

    @Test
    fun `identical sides need nothing`() {
        assertEquals(SyncAction.None, SyncPlanner.plan(local("a" to "1"), cloud("a" to "1"), 5, null))
    }

    @Test
    fun `first sync with an empty phone downloads everything`() {
        val action = SyncPlanner.plan(emptyMap(), cloud("a" to "1", "b" to "2"), 3, null)
        assertEquals(2, (action as SyncAction.Download).files.size)
    }

    @Test
    fun `first sync with an empty cloud uploads everything`() {
        val action = SyncPlanner.plan(local("a" to "1"), emptyMap(), 0, null)
        assertEquals(1, (action as SyncAction.Upload).files.size)
    }

    @Test
    fun `first sync with different content on both sides is a conflict`() {
        val action = SyncPlanner.plan(local("a" to "1"), cloud("a" to "9"), 3, null)
        assertTrue(action is SyncAction.Conflict)
    }

    @Test
    fun `only the cloud moved downloads`() {
        val action = SyncPlanner.plan(local("a" to "1"), cloud("a" to "2"), 6, baseline)
        assertEquals(listOf("a"), (action as SyncAction.Download).files.map { it.rel })
    }

    @Test
    fun `only the phone moved uploads`() {
        val action = SyncPlanner.plan(local("a" to "2"), cloud("a" to "1"), 5, baseline)
        assertEquals(listOf("a"), (action as SyncAction.Upload).files.map { it.rel })
    }

    @Test
    fun `both moved and differ is a conflict`() {
        val action = SyncPlanner.plan(local("a" to "2"), cloud("a" to "3"), 6, baseline)
        assertTrue(action is SyncAction.Conflict)
    }

    @Test
    fun `a file the cloud dropped is deleted locally when the phone did not touch it`() {
        val two = Baseline(5, mapOf("a" to "1", "b" to "2"))
        val action = SyncPlanner.plan(local("a" to "1", "b" to "2"), cloud("a" to "1"), 6, two)
        assertEquals(listOf("b"), (action as SyncAction.Download).deleteLocal)
    }

    @Test
    fun `a file deleted on the phone is deleted in the cloud`() {
        val two = Baseline(5, mapOf("a" to "1", "b" to "2"))
        val action = SyncPlanner.plan(local("a" to "1"), cloud("a" to "1", "b" to "2"), 5, two)
        assertEquals(listOf("b"), (action as SyncAction.Upload).deleteCloud.map { it.rel })
    }

    @Test
    fun `choosing a side overrides the comparison`() {
        val conflict = { side: Side -> SyncPlanner.plan(local("a" to "2"), cloud("a" to "3"), 6, baseline, side) }
        assertTrue(conflict(Side.CLOUD) is SyncAction.Download)
        assertTrue(conflict(Side.LOCAL) is SyncAction.Upload)
    }

    @Test
    fun `a phone wiped after a sync restores from the cloud and never deletes it`() {
        val synced = Baseline(5, mapOf("a" to "1", "b" to "2"))
        val action = SyncPlanner.plan(emptyMap(), cloud("a" to "1", "b" to "2"), 5, synced)
        assertEquals(2, (action as SyncAction.Download).files.size)
    }

    @Test
    fun `explicitly keeping the phone's empty state is still possible`() {
        val synced = Baseline(5, mapOf("a" to "1"))
        val action = SyncPlanner.plan(emptyMap(), cloud("a" to "1"), 5, synced, Side.LOCAL)
        assertEquals(1, (action as SyncAction.Upload).deleteCloud.size)
    }

    @Test
    fun `a change number that moved with the same content is not a cloud change`() {
        val action = SyncPlanner.plan(local("a" to "2"), cloud("a" to "1"), 99, baseline)
        assertEquals(listOf("a"), (action as SyncAction.Upload).files.map { it.rel })
    }
}
