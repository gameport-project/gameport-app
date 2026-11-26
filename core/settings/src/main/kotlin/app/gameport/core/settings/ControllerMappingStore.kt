package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerLayout
import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.ControllerMappingCodec
import app.gameport.core.model.ControllerPreset
import app.gameport.core.model.ControllerProfile
import app.gameport.core.model.PresentedControllers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The controller mapping of each game that only knows the Steam Frame's controllers: which controls
 * it uses (reported by the game's hook), whether the player customises them (off by default), and
 * the player's choices. The OpenXR layer applies them through the game's hook.
 */
@Singleton
class ControllerMappingStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_controller_mapping", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readAll())

    fun observe(appId: Int): Flow<ControllerMapping> = state.map { it[appId] ?: ControllerMapping() }

    fun get(appId: Int): ControllerMapping = state.value[appId] ?: ControllerMapping()

    /**
     * What the game's hook was told about the controls the game uses; a game seen for the first time gets the page. The profiles, [used] and [presentedResult]
     * come from a hook and a layer that may be older: when they are null or empty, what is kept is left alone and nothing is erased.
     */
    fun setDetected(
        appId: Int,
        controls: List<ControlRef>,
        source: String,
        both: Boolean = false,
        silent: Boolean = false,
        profiles: List<ControllerProfile>? = null,
        used: String? = null,
        presentedResult: String? = null,
        present: String? = null,
    ) = update(appId) { it.withDetected(controls, source, both, silent, profiles, used, presentedResult, present) }

    /** Customising starts from what the game does now, so a later change of the defaults does not move a control the player did not touch. */
    fun setEnabled(appId: Int, enabled: Boolean) = update(appId) { if (enabled && !it.enabled) it.frozenAsNow().copy(enabled = true) else it.copy(enabled = enabled) }

    /** The Steam Frame's controls for a game that also has its own: off by default. Turning it off leaves a choice of the Touch's controllers as it is. */
    fun setUseFrame(appId: Int, useFrame: Boolean) = update(appId) { it.withUseFrame(useFrame) }

    /** Which of the game's controller tables to put forward, from the next start. A new choice forgets what became of the previous one. */
    fun setPresented(appId: Int, presented: PresentedControllers) = update(appId) { it.withPresented(presented) }

    /** Applies a ready-made preset as the player's exclusive choice: replaces the links of its family, freezes the rest, switches customising on. */
    fun applyPreset(appId: Int, preset: ControllerPreset) = update(appId) { it.withPreset(preset) }

    /**
     * Sends [source] to [targets] (none: to nothing) instead of its default. Any hand-made retouch (a single link, the dialogs, a community mapping) clears the
     * chosen preset: this is the one place the menu's label is kept in step, so the choice falls back to "Personnalisé" as soon as it no longer is a whole preset.
     */
    fun setOverride(appId: Int, source: ControlRef, targets: List<ControlRef>) =
        update(appId) { it.copy(overrides = it.overrides + (source.key to ControllerLayout.encodeTargets(targets)), preset = "") }

    /** Puts the player's choices back as they were (undo and redo): whether customising is on, what each control was sent to, and the chosen preset. */
    fun restoreChoices(appId: Int, enabled: Boolean, overrides: Map<String, String>, preset: String) =
        update(appId) { it.copy(enabled = enabled, overrides = overrides, preset = preset) }

    /** Back to GamePort's own mapping. */
    fun reset(appId: Int) = update(appId) { it.copy(overrides = emptyMap(), preset = "") }

    /** The notice about this page was shown and followed. */
    fun markNoticeSeen(appId: Int) {
        if (!get(appId).noticeSeen) update(appId) { it.copy(noticeSeen = true) }
    }

    /** The text the OpenXR layer reads for [appId]: empty unless the player customises. */
    fun layerConfig(appId: Int): String {
        val mapping = get(appId)
        return if (mapping.enabled) ControllerLayout.encode(mapping.overrides) else ""
    }

    private fun update(appId: Int, change: (ControllerMapping) -> ControllerMapping) {
        val updated = change(get(appId))
        prefs.edit().putString(appId.toString(), ControllerMappingCodec.encode(updated)).apply()
        state.value = state.value + (appId to updated)
    }

    private fun readAll(): Map<Int, ControllerMapping> = prefs.all.mapNotNull { (key, value) ->
        key.toIntOrNull()?.let { appId ->
            val mapping = ControllerMappingCodec.decode(value.toString())
            // Written back in the new form, so the freezing is done once.
            if (!ControllerMappingCodec.isCurrent(value.toString())) prefs.edit().putString(key, ControllerMappingCodec.encode(mapping)).apply()
            appId to mapping
        }
    }.toMap()
}
