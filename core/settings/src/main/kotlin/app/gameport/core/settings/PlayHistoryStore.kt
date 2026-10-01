package app.gameport.core.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When each game was last started and which ones the player starred. A game is noted when GamePort
 * starts it and when the game itself reports that it started (so a launch from the system library counts too).
 */
@Singleton
class PlayHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_play_history", Context.MODE_PRIVATE)
    private val _lastPlayed = MutableStateFlow(readLastPlayed())
    private val _favorites = MutableStateFlow(readFavorites())

    /** Start time (epoch millis) by Steam app id. */
    val lastPlayed: StateFlow<Map<Int, Long>> = _lastPlayed.asStateFlow()

    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()

    fun markPlayed(appId: Int, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("$PLAYED$appId", now).apply()
        _lastPlayed.value = _lastPlayed.value + (appId to now)
    }

    /** The game is gone from this device: it no longer counts as played here. */
    fun forget(appId: Int) {
        prefs.edit().remove("$PLAYED$appId").apply()
        _lastPlayed.value = _lastPlayed.value - appId
    }

    fun toggleFavorite(appId: Int) {
        val updated = if (appId in _favorites.value) _favorites.value - appId else _favorites.value + appId
        prefs.edit().putStringSet(FAVORITES, updated.map(Int::toString).toSet()).apply()
        _favorites.value = updated
    }

    private fun readLastPlayed(): Map<Int, Long> = prefs.all.mapNotNull { (key, value) ->
        if (key.startsWith(PLAYED) && value is Long) key.removePrefix(PLAYED).toIntOrNull()?.let { it to value } else null
    }.toMap()

    private fun readFavorites(): Set<Int> = prefs.getStringSet(FAVORITES, emptySet()).orEmpty().mapNotNull(String::toIntOrNull).toSet()

    private companion object {
        const val PLAYED = "played_"
        const val FAVORITES = "favorites"
    }
}
