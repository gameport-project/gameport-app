package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.PatchDebugReceiver --ei appId <id> --es patches steam_shim,cloud_hook,xr_layer
 * Patches the installed game again with only those patches (an empty list removes them all; with no `patches` extra it is patched again with
 * everything, as the game's menu does), to
 * find out which one a game does not get along with. Android still asks to confirm the install.
 */
@AndroidEntryPoint
class PatchDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: GameInstallRepository
    @Inject lateinit var auth: SteamAuthRepository

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val all = !intent.hasExtra("patches")
        val ids = intent.getStringExtra("patches").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        Log.i("GPPatch", "app $appId: patching with " + if (all) "everything GamePort gives a game" else ids.ifEmpty { setOf("nothing but the launcher entry") }.toString())
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The app may just have been started by this broadcast: sign in before patching.
                auth.restoreSession()
                if (all) installer.repatch(appId) else installer.repatchWith(appId, ids + "vr_launcher_entry")
            } finally {
                pending.finish()
            }
        }
    }
}
