package app.gameport.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.model.ControlRef
import app.gameport.core.model.CommunityMapping
import app.gameport.core.model.ControllerAnswer
import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.ControllerPreset
import app.gameport.core.settings.ControllerMappingStore
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ControllersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: ControllerMappingStore,
    library: SteamLibraryRepository,
    device: app.gameport.core.device.DeviceProfile,
    private val feedback: app.gameport.core.settings.ControllerFeedbackStore,
    private val feedbackSender: app.gameport.core.sync.ControllerFeedbackSender,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<GameControllersRoute>().appId

    val gameName: StateFlow<String> = library.observeGame(appId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    /** The VR family of this device (`meta`, `pico`, `openxr`), null without VR: it decides how the device's controllers are drawn. */
    val deviceFamily: String? = device.vrPlatform?.id

    val mapping: StateFlow<ControllerMapping> = store.observe(appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.get(appId))

    init {
        // Opening the page answers the notice that offered it.
        store.markNoticeSeen(appId)
        // Every change of the mapping is dated: the card on the game's page waits for a run to end after it.
        store.observe(appId).onEach { feedback.noteCurrent(appId) }.launchIn(viewModelScope)
    }

    /** What the player answered for the mapping the game has now (null when nothing, or when the mapping changed since). */
    val myAnswer: StateFlow<ControllerAnswer?> = feedback.observeAnswer(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _community = MutableStateFlow<List<CommunityMapping>>(emptyList())

    /** The mappings players say work for this game on this kind of device; empty while they are read, and when the relay cannot be reached. */
    val community: StateFlow<List<CommunityMapping>> = _community

    init {
        viewModelScope.launch { _community.value = feedbackSender.communityMappings(appId) }
    }

    /** Every press sends the answer (the relay keeps the last one), whether it is the first or a change of mind. */
    fun onAnswer(answer: ControllerAnswer) = feedback.answer(appId, answer)

    /** What the player had chosen at one moment, to go back to it. */
    private data class Choices(val enabled: Boolean, val overrides: Map<String, String>, val preset: String)

    private val undone = ArrayDeque<Choices>()
    private val redone = ArrayDeque<Choices>()

    private val _history = MutableStateFlow(History())
    /** Whether there is something to undo or redo. */
    val history: StateFlow<History> = _history

    data class History(val canUndo: Boolean = false, val canRedo: Boolean = false)

    private fun now(): Choices = store.get(appId).let { Choices(it.enabled, it.overrides, it.preset) }

    /** Called before every change the player makes: it can be undone, and what was undone before it can no longer be redone. */
    private fun remember() {
        undone.addLast(now())
        if (undone.size > MAX_HISTORY) undone.removeFirst()
        redone.clear()
        publish()
    }

    private fun publish() { _history.value = History(canUndo = undone.isNotEmpty(), canRedo = redone.isNotEmpty()) }

    fun onEnabledChanged(enabled: Boolean) { remember(); store.setEnabled(appId, enabled) }

    /** Several controls sent somewhere new as one step: customising is switched on by it when it was not, and one undo takes all of it back. */
    fun onChoose(changes: Map<ControlRef, List<ControlRef>>) {
        if (changes.isEmpty()) return
        remember()
        if (!store.get(appId).enabled) store.setEnabled(appId, true)
        changes.forEach { (source, targets) -> store.setOverride(appId, source, targets) }
    }

    fun onReset() { remember(); store.reset(appId) }

    /** A ready-made preset, or automatic when null: both are one step, so one undo takes them back. */
    fun onPreset(preset: ControllerPreset?) { remember(); if (preset == null) store.reset(appId) else store.applyPreset(appId, preset) }

    fun onUndo() {
        val before = undone.removeLastOrNull() ?: return
        redone.addLast(now())
        store.restoreChoices(appId, before.enabled, before.overrides, before.preset)
        publish()
    }

    fun onRedo() {
        val after = redone.removeLastOrNull() ?: return
        undone.addLast(now())
        store.restoreChoices(appId, after.enabled, after.overrides, after.preset)
        publish()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_HISTORY = 50
    }
}
