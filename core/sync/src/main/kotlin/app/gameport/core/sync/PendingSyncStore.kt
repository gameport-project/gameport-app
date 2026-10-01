package app.gameport.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A choice the player made on the game's saves page ("use the cloud", "send mine"), applied the next
 * time the game starts, when its hook can copy files. Consumed by the sync that applies it.
 */
@Singleton
class PendingSyncStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_pending_sync", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    fun observe(appId: Int): Flow<Side?> = state.map { it[appId] }

    fun set(appId: Int, side: Side) {
        prefs.edit().putString(appId.toString(), side.name).apply()
        state.value = state.value + (appId to side)
    }

    fun clear(appId: Int) {
        prefs.edit().remove(appId.toString()).apply()
        state.value = state.value - appId
    }

    /** The pending choice, removed as it is applied. */
    fun take(appId: Int): Side? = state.value[appId]?.also { clear(appId) }

    fun clearAll() {
        prefs.edit().clear().apply()
        state.value = emptyMap()
    }

    private fun read(): Map<Int, Side> = prefs.all.mapNotNull { (key, value) ->
        val appId = key.toIntOrNull() ?: return@mapNotNull null
        appId to (runCatching { Side.valueOf(value.toString()) }.getOrNull() ?: return@mapNotNull null)
    }.toMap()
}
