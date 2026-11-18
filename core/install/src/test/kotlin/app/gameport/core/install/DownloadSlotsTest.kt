package app.gameport.core.install

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSlotsTest {
    @Test
    fun `only as many games download at once as the limit says`() = runTest {
        val slots = DownloadSlots(MutableStateFlow(2))
        val release = CompletableDeferred<Unit>()
        var started = 0
        val games = List(4) { async { slots.withSlot { started++; release.await() } } }
        advanceUntilIdle()
        assertEquals(2, started)
        assertEquals(2, slots.inUse)
        release.complete(Unit)
        games.forEach { it.await() }
        assertEquals(4, started)
        assertEquals(0, slots.inUse)
    }

    @Test
    fun `a higher limit lets the waiting games in at once`() = runTest {
        val limit = MutableStateFlow(1)
        val slots = DownloadSlots(limit)
        val release = CompletableDeferred<Unit>()
        var started = 0
        val games = List(3) { async { slots.withSlot { started++; release.await() } } }
        advanceUntilIdle()
        assertEquals(1, started)
        limit.value = 3
        advanceUntilIdle()
        assertEquals(3, started)
        release.complete(Unit)
        games.forEach { it.await() }
    }

    @Test
    fun `a place is given back when the game fails or is cancelled`() = runTest {
        val slots = DownloadSlots(MutableStateFlow(1))
        val failed = runCatching { slots.withSlot { error("download failed") } }
        assertTrue(failed.isFailure)
        assertEquals(0, slots.inUse)
        val waiting = async { slots.withSlot { CompletableDeferred<Unit>().await() } }
        advanceUntilIdle()
        assertEquals(1, slots.inUse)
        waiting.cancel()
        advanceUntilIdle()
        assertEquals(0, slots.inUse)
    }

    @Test
    fun `a limit under one still lets one game download`() = runTest {
        val slots = DownloadSlots(MutableStateFlow(0))
        var done = false
        slots.withSlot { done = true }
        assertTrue(done)
    }
}
