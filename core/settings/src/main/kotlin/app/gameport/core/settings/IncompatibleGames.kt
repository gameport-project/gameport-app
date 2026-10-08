package app.gameport.core.settings

import android.content.Context
import android.util.Log
import app.gameport.core.model.IncompatibleList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The games confirmed as incompatible. They are left out of the home unless the player chose to show them anyway, and the player is told once
 * for each of them that is in the library: [acknowledged] keeps the games already told.
 */
@Singleton
class IncompatibleGames @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("gameport_incompatible", Context.MODE_PRIVATE)
    private val _showAnyway = MutableStateFlow(prefs.getBoolean(SHOW_ANYWAY, false))
    private val _acknowledged = MutableStateFlow(prefs.getStringSet(ACKNOWLEDGED, emptySet()).orEmpty().mapNotNull(String::toIntOrNull).toSet())

    /** The list shipped with the app; empty when it cannot be read, so a mistake in it never hides a game by accident. */
    val list: IncompatibleList by lazy {
        runCatching { context.assets.open(FILE).bufferedReader().use { IncompatibleList.parse(it.readText()) } }
            .onFailure { Log.w("GPIncompatible", "$FILE not read", it) }
            .getOrDefault(IncompatibleList())
    }

    /** The player wants these games on the home anyway, to try them. */
    val showAnyway: StateFlow<Boolean> = _showAnyway.asStateFlow()

    /** The games the player has been told about (they pressed "got it"). */
    val acknowledged: StateFlow<Set<Int>> = _acknowledged.asStateFlow()

    /** The app ids to leave out of the home: none while the player shows them anyway. */
    fun hiddenIds(showAnyway: Boolean): Set<Int> = if (showAnyway) emptySet() else list.games.map { it.appId }.toSet()

    fun setShowAnyway(show: Boolean) {
        prefs.edit().putBoolean(SHOW_ANYWAY, show).apply()
        _showAnyway.value = show
    }

    fun acknowledge(appIds: Collection<Int>) {
        val updated = _acknowledged.value + appIds
        prefs.edit().putStringSet(ACKNOWLEDGED, updated.map(Int::toString).toSet()).apply()
        _acknowledged.value = updated
    }

    private companion object {
        const val FILE = "incompatible.json"
        const val SHOW_ANYWAY = "show_anyway"
        const val ACKNOWLEDGED = "acknowledged"
    }
}
