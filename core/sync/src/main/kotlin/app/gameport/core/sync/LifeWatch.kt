package app.gameport.core.sync

import android.os.IBinder
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Knows the moment a game's process is gone. The patched game gives GamePort a witness of life (see the hook), and Android tells GamePort at once
 * when the process that holds it dies, whether the game quit by itself or was killed from the menu of the headset. A game patched before has no
 * witness: its end is then found out later, from its signs of life (see [VerdictAsker]). Only what lives in memory: if GamePort is stopped, it
 * is given a witness again with the next sign the game gives.
 */
@Singleton
class LifeWatch @Inject constructor(
    private val playtime: PlaytimeTracker,
    private val asker: VerdictAsker,
) {
    private class Watch(val witness: IBinder, val recipient: IBinder.DeathRecipient)

    private val watched = HashMap<String, Watch>()

    fun watch(packageName: String, witness: IBinder) {
        val gone = IBinder.DeathRecipient { died(packageName, witness) }
        synchronized(this) {
            val before = watched[packageName]
            // The same witness is not followed twice, and an older one is let go.
            if (before?.witness === witness) return
            before?.let { runCatching { it.witness.unlinkToDeath(it.recipient, 0) } }
            watched.remove(packageName)
            try {
                witness.linkToDeath(gone, 0)
                watched[packageName] = Watch(witness, gone)
                return
            } catch (e: android.os.RemoteException) {
                // Already gone when it arrived.
            }
        }
        died(packageName, witness)
    }

    private fun died(packageName: String, witness: IBinder) {
        synchronized(this) {
            if (watched[packageName]?.witness !== witness && watched.containsKey(packageName)) return // a newer witness took its place
            watched.remove(packageName)
        }
        Log.i(TAG, "$packageName: its process is gone")
        runCatching {
            // The time played stops, and the question about the run is asked.
            playtime.paused(packageName)
            asker.gameClosed(packageName)
        }.onFailure { Log.w(TAG, "$packageName: the end of its process could not be dealt with", it) }
    }

    private companion object {
        const val TAG = "GPVerdict"
    }
}
