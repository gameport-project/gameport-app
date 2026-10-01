package app.gameport.core.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The time each game was played on this device, counted by GamePort while the game is on screen. */
@Singleton
class PlaytimeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_playtime", Context.MODE_PRIVATE)
    private val totals = MutableStateFlow(prefs.all.mapNotNull { (key, value) -> key.toIntOrNull()?.let { id -> (value as? Long)?.let { id to it } } }.toMap())

    fun observe(appId: Int): Flow<Long> = totals.map { it[appId] ?: 0L }

    fun get(appId: Int): Long = totals.value[appId] ?: 0L

    @Synchronized
    fun add(appId: Int, millis: Long) {
        if (millis <= 0) return
        val updated = get(appId) + millis
        prefs.edit().putLong(appId.toString(), updated).apply()
        totals.value = totals.value + (appId to updated)
    }
}
