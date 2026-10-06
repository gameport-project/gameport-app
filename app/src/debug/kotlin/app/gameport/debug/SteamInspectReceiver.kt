package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.steam.session.SteamInspector
import app.gameport.core.steam.session.SteamSessionHolder
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.SteamInspectReceiver --ei appId <id> [--ei depot <id> --es manifest <gid>]
 * Writes what Steam publishes for the app, as the signed-in account sees it (depots, branches, settings), to files/inspect/<appId>.txt,
 * and with a depot and a manifest the list of its files with their sizes and SHA-1 to files/inspect/<appId>-<depot>.txt. Read only.
 */
@AndroidEntryPoint
class SteamInspectReceiver : BroadcastReceiver() {
    @Inject lateinit var sessions: SteamSessionHolder

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val depot = intent.getIntExtra("depot", 0)
        val manifest = intent.getStringExtra("manifest")?.toLongOrNull()
        val branch = intent.getStringExtra("branch") ?: "public"
        CoroutineScope(Dispatchers.IO).launch {
            val session = sessions.current.value
            if (session == null) {
                Log.w(TAG, "not signed in")
                return@launch
            }
            val directory = File(context.filesDir, "inspect").apply { mkdirs() }
            runCatching {
                File(directory, "$appId.txt").writeText(SteamInspector.describe(session, appId))
                Log.i(TAG, "app $appId described")
                if (depot != 0 && manifest != null) {
                    File(directory, "$appId-$depot-$manifest.txt").writeText(SteamInspector.manifest(session, appId, depot, manifest, branch))
                    Log.i(TAG, "depot $depot manifest $manifest listed")
                }
            }.onFailure { Log.w(TAG, "inspection failed", it) }
        }
    }

    private companion object {
        const val TAG = "GPInspect"
    }
}
