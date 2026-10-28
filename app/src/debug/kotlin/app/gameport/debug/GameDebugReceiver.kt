package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.GameDebugReceiver --ei appId <id> --es action <state|install|dlc-install|dlc-remove> [--es dlcs <id,id>] [--ei dlc <id>] (also `uninstall`)
 * Test only. Does what the buttons of a game's page and its settings do, for a game, without the screen, and logs what it finds (tag GPGame): `state` tells whether the game
 * is in the library, what it is installed as and which extra content it has; `install` installs it with the extra content of `dlcs` (none by default); `dlc-install` and
 * `dlc-remove` add or take off one extra content of an installed game. The system still asks to confirm an installation.
 */
@AndroidEntryPoint
class GameDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: GameInstallRepository
    @Inject lateinit var library: SteamLibraryRepository
    @Inject lateinit var auth: SteamAuthRepository

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val action = intent.getStringExtra("action") ?: "state"
        val dlcs = intent.getStringExtra("dlcs").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        val dlc = intent.getIntExtra("dlc", 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.restoreSession()
                val game = withTimeoutOrNull(60_000) { library.observeGame(appId).first { it != null } }
                if (game == null) {
                    Log.i(TAG, "app $appId: not in the library")
                    return@launch
                }
                val extra = game.androidBuild?.dlc.orEmpty()
                when (action) {
                    "state" -> {
                        val (installed, removable) = installer.observeInstalledDlc(appId).first()
                        Log.i(TAG, "app $appId ${game.name}: state=${installer.observe(appId).first()} ownership=${game.ownership} package=${installer.installedPackage(appId)} dlc owned=${extra.count { it.owned }} of ${extra.size} " +
                            "installed=$installed removable=$removable work=${installer.observeDlcWork(appId).first()}")
                        extra.filter { it.owned }.forEach { Log.i(TAG, "  dlc ${it.appId} ${it.name}: ${it.downloadBytes / 1_000_000} MB, depots ${it.depots.map { d -> "${d.id}@${d.declaredBy ?: "game"}" }}") }
                    }
                    "install" -> {
                        Log.i(TAG, "app $appId: install with extra content $dlcs")
                        installer.install(game, dlcs)
                    }
                    "uninstall" -> installer.uninstall(appId)
                    "dlc-install" -> installer.installDlc(game, dlc)
                    "dlc-remove" -> installer.removeDlc(game, dlc)
                    else -> Log.i(TAG, "unknown action $action")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GPGame"
    }
}
