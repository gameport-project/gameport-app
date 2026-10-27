package app.gameport.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** How a game's saves are kept in Steam Cloud, as the player chose. */
enum class SaveSpaceMode {
    /** The Android saves, Steam's way for Android builds. The default. */
    ANDROID,

    /**
     * Each time the game starts, the Windows saves feed this device (nothing is written to them), and this device feeds the Android saves,
     * as usual.
     */
    USE_PC,

    /** As [USE_PC], and this device also writes to the Windows saves, both ways. The player asked for it, knowing the risk. */
    USE_PC_AND_SEND,
    ;

    val usesPc: Boolean get() = this != ANDROID
}

/** Remembers, per game, how its saves are kept (see [SaveSpaceMode]). */
@Singleton
class SaveSpaceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_save_space", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    fun get(appId: Int): SaveSpaceMode = state.value[appId] ?: SaveSpaceMode.ANDROID

    fun observe(appId: Int): Flow<SaveSpaceMode> = state.map { it[appId] ?: SaveSpaceMode.ANDROID }

    fun set(appId: Int, mode: SaveSpaceMode) {
        if (mode == SaveSpaceMode.ANDROID) prefs.edit().remove(appId.toString()).apply() else prefs.edit().putString(appId.toString(), mode.name).apply()
        state.value = if (mode == SaveSpaceMode.ANDROID) state.value - appId else state.value + (appId to mode)
    }

    /** How Steam described the game's saves the last time GamePort looked, to notice a change. Null when it never did. */
    fun rulesSignature(appId: Int): String? = prefs.getString("rules.$appId", null)

    fun setRulesSignature(appId: Int, signature: String) {
        prefs.edit().putString("rules.$appId", signature).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        state.value = emptyMap()
    }

    private fun read(): Map<Int, SaveSpaceMode> = prefs.all.mapNotNull { (key, value) ->
        val appId = key.toIntOrNull() ?: return@mapNotNull null
        appId to (runCatching { SaveSpaceMode.valueOf(value.toString()) }.getOrNull() ?: return@mapNotNull null)
    }.toMap()
}
