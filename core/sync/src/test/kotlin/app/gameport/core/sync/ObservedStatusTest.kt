package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedStatusTest {
    private val same = mapOf("a" to "1")
    private val other = mapOf("a" to "2")

    @Test
    fun `saves that differ from the cloud are to be sent`() {
        assertEquals(SyncStatus.PENDING, statusAfterObserving(SyncStatus.OK, other, same))
        assertEquals(SyncStatus.PENDING, statusAfterObserving(null, other, same))
    }

    @Test
    fun `a new save the cloud does not have is to be sent`() {
        assertEquals(SyncStatus.PENDING, statusAfterObserving(SyncStatus.OK, mapOf("a" to "1", "b" to "1"), same))
    }

    @Test
    fun `saves that agree end what was to be sent`() {
        assertEquals(SyncStatus.OK, statusAfterObserving(SyncStatus.PENDING, same, same))
        assertEquals(null, statusAfterObserving(SyncStatus.OK, same, same))
    }

    @Test
    fun `a sync that did not go through is not hidden by looking at the saves`() {
        assertEquals(null, statusAfterObserving(SyncStatus.OFFLINE, other, same))
        assertEquals(null, statusAfterObserving(SyncStatus.FAILED, other, same))
        assertEquals(null, statusAfterObserving(SyncStatus.OFFLINE, same, same))
    }
}
