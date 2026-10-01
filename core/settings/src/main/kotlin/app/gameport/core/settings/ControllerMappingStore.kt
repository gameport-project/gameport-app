package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerLayout
import app.gameport.core.model.ControllerMapping
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

    /** What the game's hook was told about the controls the game uses; a game seen for the first time gets the page. */
    fun setDetected(appId: Int, controls: List<ControlRef>, source: String) =
        update(appId) { it.copy(detected = controls.sortedBy { c -> c.key }, source = source) }

    fun setEnabled(appId: Int, enabled: Boolean) = update(appId) { it.copy(enabled = enabled) }

    /** Sends [source] to [target] (null: to nothing) instead of its default. */
    fun setOverride(appId: Int, source: ControlRef, target: ControlRef?) =
        update(appId) { it.copy(overrides = it.overrides + (source.key to (target?.key ?: "none"))) }

    /** Back to GamePort's own mapping. */
    fun reset(appId: Int) = update(appId) { it.copy(overrides = emptyMap()) }

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
        prefs.edit().putString(appId.toString(), encode(updated)).apply()
        state.value = state.value + (appId to updated)
    }

    private fun encode(mapping: ControllerMapping): String = listOf(
        mapping.detected.joinToString(",") { it.key },
        if (mapping.enabled) "1" else "0",
        mapping.overrides.entries.joinToString(";") { "${it.key}=${it.value}" },
        if (mapping.noticeSeen) "1" else "0",
        mapping.source,
    ).joinToString("|")

    private fun decode(text: String): ControllerMapping {
        val parts = text.split('|')
        return ControllerMapping(
            detected = parts.getOrNull(0).orEmpty().split(',').mapNotNull(ControlRef::parse),
            enabled = parts.getOrNull(1) == "1",
            overrides = parts.getOrNull(2).orEmpty().split(';').mapNotNull { entry ->
                entry.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() }?.let { it[0] to it[1] }
            }.toMap(),
            noticeSeen = parts.getOrNull(3) == "1",
            source = parts.getOrNull(4).orEmpty(),
        )
    }

    private fun readAll(): Map<Int, ControllerMapping> = prefs.all.mapNotNull { (key, value) ->
        key.toIntOrNull()?.let { it to decode(value.toString()) }
    }.toMap()
}
