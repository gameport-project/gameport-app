package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.steam.GameDownloader
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.OriginalDownloadReceiver --ei appId <id>
 * Test only. Downloads the base Android depots of a game from Steam, as they are published, into files/original/<appId>, without patching or
 * installing anything: to try a game as Steam sells it, to tell what is the game's and what is the patch's. Says where it ended (tag GPOriginal).
 */
@AndroidEntryPoint
class OriginalDownloadReceiver : BroadcastReceiver() {
    @Inject lateinit var auth: SteamAuthRepository
    @Inject lateinit var library: SteamLibraryRepository
    @Inject lateinit var downloader: GameDownloader

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.restoreSession()
                val game = withTimeoutOrNull(60_000) { library.observeGame(appId).first { it != null } }
                val depots = game?.androidBuild?.baseDepots.orEmpty()
                if (depots.isEmpty()) {
                    Log.w(TAG, "app $appId: no base depot found")
                    return@launch
                }
                val directory = File(context.getExternalFilesDir(null), "original/$appId").apply { mkdirs() }
                Log.i(TAG, "app $appId: downloading depots ${depots.map { it.id }} to ${directory.path}")
                downloader.download(appId, depots.map { it.id }, directory, depots.associate { it.id to it.downloadBytes }) { fraction, _ ->
                    if (fraction >= 0f && (fraction * 100).toInt() % 20 == 0) Log.i(TAG, "app $appId: ${(fraction * 100).toInt()}%")
                }
                Log.i(TAG, "app $appId: done: ${directory.walkTopDown().filter { it.isFile }.joinToString { it.name + " " + it.length() }}")
            } catch (e: Exception) {
                Log.w(TAG, "app $appId: failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GPOriginal"
    }
}
