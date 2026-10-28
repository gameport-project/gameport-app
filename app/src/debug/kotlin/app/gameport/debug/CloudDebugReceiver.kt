package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.steam.session.CloudUpload
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.fetchCloud
import app.gameport.core.steam.session.uploadToCloud
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.CloudDebugReceiver --ei appId <id>
 * Logs (tag GPCloud) the cloud files of that app for the signed-in account.
 */
@AndroidEntryPoint
class CloudDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var sessions: SteamSessionHolder

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = sessions.current.value
                if (session == null) {
                    Log.w(TAG, "not signed in")
                    return@launch
                }
                intent.getStringExtra("delete")?.let { name ->
                    val result = session.uploadToCloud(appId, emptyList(), listOf(name), "GamePort debug", 1L)
                    Log.i(TAG, "delete of $name -> new change number $result")
                }
                // `--es upload <cloud name> --es from <file in cache/debug-upload> [--es time <millis>]`: sends that one file under that name, to find out which names Steam accepts.
                intent.getStringExtra("upload")?.let { name ->
                    val file = File(File(context.cacheDir, "debug-upload"), intent.getStringExtra("from").orEmpty().substringAfterLast('/'))
                    if (!file.isFile) {
                        Log.w(TAG, "upload of $name: no file at ${file.path}")
                    } else {
                        val sha = MessageDigest.getInstance("SHA-1").digest(file.readBytes())
                        val time = intent.getStringExtra("time")?.toLongOrNull() ?: file.lastModified()
                        val result = session.uploadToCloud(appId, listOf(CloudUpload(name, file, sha, time)), emptyList(), "GamePort debug", 1L)
                        Log.i(TAG, "upload of $name (${file.length()} B) -> new change number $result")
                    }
                }
                val snapshot = session.fetchCloud(appId)
                Log.i(TAG, "app $appId: change number ${snapshot.changeNumber}, ${snapshot.files.size} file(s)")
                snapshot.files.forEach { Log.i(TAG, "  ${it.name.replace("gameport-backup/", "GPB/")}  ${it.sizeBytes} B  sha1=${it.sha1}  t=${it.timestampMillis} state=${it.state}") }
            } catch (e: Exception) {
                Log.e(TAG, "cloud query failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GPCloud"
    }
}
