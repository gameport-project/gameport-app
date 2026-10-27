package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.sync.LocalSavesCleaner
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.ClearSavesDebugReceiver --ei appId <id>
 * Does what the button "Clear this device's saves" of the saves page does, and logs what came of it (tag GPClear). The saves of the game are moved to the
 * backup folder of the device; nothing is removed from Steam.
 */
@AndroidEntryPoint
class ClearSavesDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var cleaner: LocalSavesCleaner

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = cleaner.clear(appId)
                Log.i("GPClear", "app $appId: ${outcome.result}, ${outcome.moved} file(s) put aside")
            } finally {
                pending.finish()
            }
        }
    }
}
