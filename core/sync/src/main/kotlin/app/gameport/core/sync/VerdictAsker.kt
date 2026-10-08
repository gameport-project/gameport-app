package app.gameport.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.OfflineEvidence
import app.gameport.core.settings.GameVerdicts
import app.gameport.core.settings.IncompatibleGames
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides, when a game closes, whether to ask the player if it worked. The question waits in [GameVerdicts] until GamePort is on screen. Nothing
 * is asked for a launch the player cancelled, for a process GamePort started only to send saves, or for a game known to be incompatible.
 */
@Singleton
class VerdictAsker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: InstalledGames,
    private val verdicts: GameVerdicts,
    private val incompatible: IncompatibleGames,
    private val decisions: PlayDecisions,
    private val coordinator: CloudSyncCoordinator,
    private val auth: SteamAuthRepository,
) {
    fun gameClosed(packageName: String) {
        // The window about another device is told once per run, whatever follows.
        val otherDeviceAsked = decisions.consumeAsked(packageName)
        if (decisions.consumeQuit(packageName)) return
        if (coordinator.isCatchUp(packageName)) return
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return
        if (incompatible.list.game(appId) != null) return
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val offlineRun = OfflineEvidence.qualifies(auth.connection.value, networkAvailable(), otherDeviceAsked)
        verdicts.update { it.closed(appId, version, LocalDate.now().toString(), offlineRun) }
    }

    /** The device has a connection that reaches the internet (it does not say that Steam can be reached). */
    private fun networkAvailable(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
