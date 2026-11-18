package app.gameport.core.install

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * The places for downloads: how many games download at the same time is [limit], which the player can change while games are waiting. A game takes a place when it starts to
 * download and gives it back when it is done, however it ends. A change of the limit is seen by the games that wait (a higher one lets them in at once); the games already
 * downloading are not stopped by a lower one, the places are just not given again before enough of them are free.
 */
class DownloadSlots(private val limit: StateFlow<Int>) {
    private val running = MutableStateFlow(0)

    /** How many games hold a place now. */
    val inUse: Int get() = running.value

    suspend fun <T> withSlot(block: suspend () -> T): T {
        while (true) {
            // Waits until a place is free for the limit as it is now, then takes it; two games that see it free at the same time cannot both take the last one.
            combine(running, limit) { used, allowed -> used < allowed.coerceAtLeast(1) }.first { it }
            val used = running.value
            if (used < limit.value.coerceAtLeast(1) && running.compareAndSet(used, used + 1)) break
        }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }
}
