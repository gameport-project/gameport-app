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
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.GameDebugReceiver --ei appId <id> --es action <state|install|dlc-install|dlc-remove|packagename|cancel> [--es dlcs <id,id>] [--ei dlc <id>] [--es mode <auto|on|off>] [--ez repatch false] (also `uninstall`)
 * Test only. Does what the buttons of a game's page and its settings do, for a game, without the screen, and logs what it finds (tag GPGame): `state` tells whether the game
 * is in the library, what it is installed as and which extra content it has; `install` installs it with the extra content of `dlcs` (none by default); `dlc-install` and
 * `dlc-remove` add or take off one extra content of an installed game. The system still asks to confirm an installation.
 */
@AndroidEntryPoint
class GameDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: GameInstallRepository
    @Inject lateinit var library: SteamLibraryRepository
    @Inject lateinit var auth: SteamAuthRepository
    @Inject lateinit var workshop: app.gameport.core.install.WorkshopRepository
    @Inject lateinit var gameSettings: app.gameport.core.settings.GameSettingsStore
    @Inject lateinit var issues: app.gameport.core.sync.GameIssuesRepository
    @Inject lateinit var sessions: app.gameport.core.steam.session.SteamSessionHolder

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
                        // The name GamePort gives a game on a device whose store refuses a re-signed one, and whether installing it again would change it.
                        Log.i(TAG, "app $appId: name wanted=${installer.renamedPackageFor(appId)} installed=${installer.installedPackage(appId)} needsRename=${installer.needsRename(appId)}")
                        // The notices the game's page builds, as the player sees them.
                        Log.i(TAG, "app $appId: issues=${issues.observe(appId).first()}")
                        extra.filter { it.owned }.forEach { Log.i(TAG, "  dlc ${it.appId} ${it.name}: ${it.downloadBytes / 1_000_000} MB, depots ${it.depots.map { d -> "${d.id}@${d.declaredBy ?: "game"}" }}") }
                    }
                    "install" -> {
                        Log.i(TAG, "app $appId: install with extra content $dlcs")
                        installer.install(game, dlcs)
                    }
                    "uninstall" -> installer.uninstall(appId)
                    "cancel" -> {
                        Log.i(TAG, "app $appId: its install is given up")
                        installer.cancelInstalling(appId)
                    }
                    "repair" -> installer.repair(game)
                    "features" -> {
                        // What the headset's runtime reads to decide whether a game may use the alpha blend mode: the features the installed package asks for (0 not declared, 1 required, 2 declared not required).
                        val pkg = installer.installedPackage(appId) ?: return@launch
                        val info = context.packageManager.getPackageInfo(pkg, android.content.pm.PackageManager.GET_CONFIGURATIONS)
                        val features = info.reqFeatures.orEmpty()
                        val flag = features.firstOrNull { it.name == "com.oculus.feature.PASSTHROUGH" }?.flags
                        Log.i(TAG, "app $appId $pkg: ${features.size} requested feature(s), passthrough declared=${flag != null} flags=$flag (runtime result ${if (flag == null) 0 else if (flag != 0) 1 else 2}); " + features.joinToString { "${it.name}:${it.flags}" })
                    }
                    "subscribed" -> {
                        pending.finish()
                        val session = sessions.current.value
                        Log.i(TAG, "app $appId subscribed workshop (${intent.getStringExtra("type") ?: "mysubscriptions"}): " + if (session == null) "not signed in" else app.gameport.core.steam.session.SteamInspector.subscribedWorkshop(session, appId, intent.getStringExtra("type") ?: "mysubscriptions"))
                    }
                    "passthrough" -> {
                        val mode = when (intent.getStringExtra("mode")) {
                            "on" -> app.gameport.core.model.PassthroughMode.ON
                            "off" -> app.gameport.core.model.PassthroughMode.OFF
                            else -> app.gameport.core.model.PassthroughMode.AUTO
                        }
                        gameSettings.set(appId, gameSettings.get(appId).copy(passthrough = mode))
                        Log.i(TAG, "app $appId: passthrough set to $mode, patching again")
                        installer.repatch(appId)
                    }
                    "packagename" -> {
                        val mode = when (intent.getStringExtra("mode")) {
                            "on" -> app.gameport.core.model.PackageNameMode.ON
                            "off" -> app.gameport.core.model.PackageNameMode.OFF
                            else -> app.gameport.core.model.PackageNameMode.AUTO
                        }
                        val before = installer.installedPackage(appId)
                        gameSettings.set(appId, gameSettings.get(appId).copy(packageName = mode))
                        // The choice can be written without patching again: that is how a game left under its own name meets the choice that wants it renamed, which is what the page reacts to.
                        val repatch = intent.getBooleanExtra("repatch", true)
                        Log.i(TAG, "app $appId: package name set to $mode (installed as $before, will be ${installer.renamedPackageFor(appId) ?: before})${if (repatch) ", patching again" else ", not patching"}")
                        if (repatch) installer.repatch(appId)
                    }
                    "workshop" -> {
                        // A broadcast held open for minutes is reported as GamePort not answering: it is let go at once.
                        pending.finish()
                        val item = intent.getStringExtra("item")?.toLongOrNull() ?: return@launch
                        val packageName = installer.installedPackage(appId) ?: return@launch
                        repeat(240) {
                            val state = workshop.request(packageName, listOf(item)).items.first()
                            Log.i(TAG, "workshop $item: ${state.phase} ${(state.fraction * 100).toInt()}% ${state.sizeOnDisk / 1024} KB '${state.title}' by ${state.creator} ${state.error}")
                            if (state.phase == app.gameport.core.install.WorkshopRepository.Phase.INSTALLED || state.phase == app.gameport.core.install.WorkshopRepository.Phase.FAILED) return@launch
                            kotlinx.coroutines.delay(1_000)
                        }
                    }
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
