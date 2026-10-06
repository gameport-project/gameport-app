package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.steam.AchievementUpload
import app.gameport.core.steam.AchievementUploader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.AchievementDebugReceiver --ei appId <id> --es names NAME1,NAME2
 * Logs (tag GPAchievements) what adding those achievements to the Steam account would change. Nothing is sent.
 *
 * Add `--ez send true` to really add them. That writes to the account and cannot be undone from GamePort.
 */
@AndroidEntryPoint
class AchievementDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var uploader: AchievementUploader

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val names = intent.getStringExtra("names").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val send = intent.getBooleanExtra("send", false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = uploader.upload(appId, names, dryRun = !send)
                Log.i(TAG, "app $appId ${if (send) "SEND" else "dry run"} $names -> ${describe(outcome)}")
            } catch (e: Exception) {
                Log.e(TAG, "app $appId: failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun describe(outcome: AchievementUpload) = when (outcome) {
        is AchievementUpload.Sent -> "sent ${outcome.names}"
        is AchievementUpload.Planned -> "would add ${outcome.names} by changing the stats ${outcome.stats} (already there ${outcome.already}, unknown ${outcome.unknown})"
        is AchievementUpload.NothingToSend -> "nothing to send (already there ${outcome.already}, unknown ${outcome.unknown})"
        AchievementUpload.Offline -> "Steam cannot be reached"
        is AchievementUpload.Refused -> "refused: ${outcome.reason}"
    }

    private companion object {
        const val TAG = "GPAchievements"
    }
}
