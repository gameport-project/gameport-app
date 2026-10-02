package app.gameport.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.device.DeviceProfile
import app.gameport.core.install.AppUpdater
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.AppUpdateState
import app.gameport.core.model.AppLanguage
import app.gameport.core.model.AuthState
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.SpeedUnit
import app.gameport.core.model.PlayerDefaults
import app.gameport.core.settings.GameSettingsStore
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.sync.CloudSyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: UserSettings,
    private val auth: SteamAuthRepository,
    private val installer: GameInstallRepository,
    private val cloudSync: CloudSyncCoordinator,
    private val gameSettings: GameSettingsStore,
    private val updater: AppUpdater,
    device: DeviceProfile,
) : ViewModel() {
    /** Height and seated defaults only exist on a VR device. */
    val isHeadset: Boolean = device.isHeadset

    /** The configuration every game uses unless it has its own. */
    val playerDefaults: StateFlow<PlayerDefaults> = gameSettings.defaults

    fun onDefaultHeightChanged(heightCm: Int) = gameSettings.setDefaults(PlayerDefaults(heightCm))

    val speedUnit: StateFlow<SpeedUnit> = settings.speedUnit

    val display: StateFlow<DisplaySettings> = settings.display

    fun onDisplayChanged(change: (DisplaySettings) -> DisplaySettings) = settings.updateDisplay(change)

    val language: StateFlow<AppLanguage> = settings.language

    fun onLanguageSelected(language: AppLanguage) = settings.setLanguage(language)

    /** Display name of the signed-in Steam account, if any. */
    val accountName: StateFlow<String?> = auth.authState
        .map { (it as? AuthState.SignedIn)?.account?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** True when the player chose offline mode. */
    val offline: StateFlow<Boolean> = auth.connection
        .map { it == app.gameport.core.model.SteamConnection.OFFLINE_MODE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun onOfflineModeChanged(enabled: Boolean) {
        viewModelScope.launch { auth.setOfflineMode(enabled) }
    }

    val countPlaytimeOnSteam: StateFlow<Boolean> = settings.countPlaytimeOnSteam

    fun onCountPlaytimeOnSteamChanged(count: Boolean) = settings.setCountPlaytimeOnSteam(count)

    val returnToGamePort: StateFlow<Boolean> = settings.returnToGamePort

    fun onReturnToGamePortChanged(enabled: Boolean) = settings.setReturnToGamePort(enabled)

    fun onSpeedUnitSelected(unit: SpeedUnit) = settings.setSpeedUnit(unit)

    /** Forgets the account and every unfinished download. Installed games stay. */
    fun onSignOut() {
        viewModelScope.launch {
            installer.discardAll()
            cloudSync.forgetSyncState()
            auth.signOut()
        }
    }

    val installedVersion: String = updater.installedVersion

    val canUpdateInPlace: Boolean = updater.canUpdateInPlace

    val updateState: StateFlow<AppUpdateState> = updater.state

    /** What stops an update from starting now, if anything. */
    val updateBlocker = updater.blocker.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val updateCheckHours: StateFlow<Int> = settings.updateCheckHours

    fun onUpdateCheckHoursChanged(hours: Int) = settings.setUpdateCheckHours(hours)

    fun onCheckForUpdate() = updater.check(manual = true)

    fun onUpdate() = updater.update()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
