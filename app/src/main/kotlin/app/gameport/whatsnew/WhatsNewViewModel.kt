package app.gameport.whatsnew

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.AuthState
import app.gameport.core.model.PatchAllState
import app.gameport.core.model.WhatsNew
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import app.gameport.core.sync.PlaytimeTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the news window shows. [games] is empty when no game has to be patched again: then there is no alert and no button to patch. */
data class WhatsNewState(
    val version: String,
    val items: List<WhatsNew.Item>,
    val confirmedGames: List<String>,
    /** The games to patch again, by name. */
    val games: List<String>,
    /** The games left out because they are on screen, by name. */
    val onScreen: List<String>,
    val progress: PatchAllState?,
    val currentName: String?,
    val failedNames: List<String>,
    val preview: Boolean,
)

/**
 * Tells the player what a new GamePort changes, once for each version, and asks to patch the games again when they need it. It stays until
 * the player validates it (or has patched the games); "see later" only hides it until GamePort is opened again. It is shown for a version
 * that brings something, such as compatibility, even when no game has to be patched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    @ApplicationContext context: Context,
    auth: SteamAuthRepository,
    private val installer: GameInstallRepository,
    private val library: SteamLibraryRepository,
    private val settings: UserSettings,
    private val installedGames: InstalledGames,
    private val playtime: PlaytimeTracker,
    private val preview: WhatsNewPreview,
    private val postponed: WhatsNewPostponed,
) : ViewModel() {
    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    private val versionCode = packageInfo.longVersionCode.toInt()
    private val versionName = packageInfo.versionName.orEmpty()

    /** What was decided when "patch all" was pressed, kept so the window still has its text once the games are up to date. */
    private class Run(val ids: List<Int>, val names: Map<Int, String>, val notes: WhatsNew.Summary, val onScreen: List<String>)

    private val run = MutableStateFlow<Run?>(null)
    private val simulated = MutableStateFlow<PatchAllState?>(null)

    private class Inputs(val signedIn: Boolean, val outdated: Set<Int>, val seen: Int, val progress: PatchAllState?, val run: Run?, val postponed: Boolean)

    init {
        // A first install has nothing to catch up on.
        if (settings.whatsNewSeen.value == 0 && installedGames.all().isEmpty()) settings.markWhatsNewSeen(versionCode)
    }

    private val real = combine(
        combine(auth.authState, installer.observeOutdatedPatches(), settings.whatsNewSeen, installer.patchAll, run) { authState, outdated, seen, progress, run ->
            Inputs(authState is AuthState.SignedIn, outdated, seen, progress, run, false)
        },
        postponed.value,
    ) { input, later -> Inputs(input.signedIn, input.outdated, input.seen, input.progress, input.run, later) }.mapLatest { input ->
        val running = input.run
        when {
            running != null -> WhatsNewState(
                version = versionName,
                items = running.notes.items,
                confirmedGames = running.notes.confirmedGames,
                games = running.ids.mapNotNull(running.names::get),
                onScreen = running.onScreen,
                progress = input.progress,
                currentName = input.progress?.current?.let(running.names::get),
                failedNames = input.progress?.failed.orEmpty().mapNotNull(running.names::get),
                preview = false,
            )
            input.signedIn && input.seen < versionCode && !input.postponed -> {
                val notes = WhatsNew.between(input.seen, versionCode)
                if (notes.isEmpty) null else {
                    val (waiting, busy) = input.outdated.sorted().partition { !isOnScreen(it) }
                    WhatsNewState(versionName, notes.items, notes.confirmedGames, waiting.map { nameOf(it) }, busy.map { nameOf(it) }, null, null, emptyList(), false)
                }
            }
            else -> null
        }
    }

    val state: StateFlow<WhatsNewState?> = combine(real, preview.mode, simulated) { real, previewing, simulated ->
        if (previewing != null) previewState(previewing, simulated) else real
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPatchAll() {
        if (preview.mode.value != null) return simulate()
        viewModelScope.launch {
            val outdated = withTimeoutOrNull(5_000) { installer.observeOutdatedPatches().first() } ?: return@launch
            val notes = WhatsNew.between(settings.whatsNewSeen.value, versionCode)
            val (ids, busy) = outdated.sorted().partition { !isOnScreen(it) }
            run.value = Run(ids, outdated.associateWith { nameOf(it) }, notes, busy.map { nameOf(it) })
            installer.repatchAll(ids)
        }
    }

    /** Stops the patching: the game being patched is stopped too, and the ones after it are left as they are. */
    fun onStop() {
        if (preview.mode.value != null) {
            simulation?.cancel()
            simulated.value = PatchAllState(previewNames.size, simulated.value?.done ?: 0, null, previewNames.indices.drop(simulated.value?.done ?: 0), finished = true)
            return
        }
        installer.cancelPatchAll()
    }

    /** "See later": the window goes away for now and comes back the next time GamePort is opened. */
    fun onLater() {
        if (preview.mode.value != null) return onClose()
        postponed.postpone()
    }

    /** Validates the news for good: the window is not shown again for this version (the player understood, or the games were patched). */
    fun onClose() {
        if (preview.mode.value != null) {
            preview.hide()
            simulated.value = null
            return
        }
        settings.markWhatsNewSeen(versionCode)
        run.value = null
        installer.clearPatchAll()
    }

    private suspend fun nameOf(appId: Int): String =
        withTimeoutOrNull(2_000) { library.observeGame(appId).first() }?.name ?: installedGames.all()[appId] ?: "#$appId"

    private fun isOnScreen(appId: Int): Boolean = installedGames.all()[appId]?.let(playtime::isOnScreen) == true

    // The made-up games of the preview.
    private val previewNames = listOf("SUPERHOT VR", "Moss 2", "Ancient Dungeon VR", "Underdogs")

    private fun previewState(patchNeeded: Boolean, progress: PatchAllState?): WhatsNewState {
        val notes = WhatsNew.between(0, Int.MAX_VALUE)
        return WhatsNewState(
            version = WhatsNew.latestName,
            items = notes.items,
            confirmedGames = notes.confirmedGames,
            games = if (patchNeeded) previewNames else emptyList(),
            onScreen = emptyList(),
            progress = progress,
            currentName = progress?.current?.let { previewNames.getOrNull(it) },
            failedNames = progress?.failed.orEmpty().mapNotNull(previewNames::getOrNull),
            preview = true,
        )
    }

    private var simulation: kotlinx.coroutines.Job? = null

    private fun simulate() {
        simulation = viewModelScope.launch {
            for (index in previewNames.indices) {
                simulated.value = PatchAllState(previewNames.size, index, index, emptyList(), finished = false)
                delay(1_800)
            }
            simulated.value = PatchAllState(previewNames.size, previewNames.size, null, emptyList(), finished = true)
        }
    }
}
