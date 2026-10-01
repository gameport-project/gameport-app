package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.GameSettings
import app.gameport.core.model.PlayerDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Settings kept on the device: what applies to every game ([defaults]) and what a game overrides.
 * A game only holds its own height once the player set one; changing the defaults never touches it.
 */
@Singleton
class GameSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(0)
    private val _defaults = MutableStateFlow(readDefaults())

    val defaults: StateFlow<PlayerDefaults> = _defaults.asStateFlow()

    fun setDefaults(value: PlayerDefaults) {
        val height = value.heightCm.coerceIn(PlayerDefaults.HEIGHT_RANGE_CM)
        prefs.edit().putInt(KEY_DEFAULT_HEIGHT, height).apply()
        _defaults.value = PlayerDefaults(height)
        changes.value += 1
    }

    fun get(appId: Int): GameSettings = GameSettings(
        seated = prefs.getBoolean("seated_$appId", false),
        heightCm = if (prefs.contains("height_$appId")) prefs.getInt("height_$appId", 0) else null,
    )

    fun observe(appId: Int): Flow<GameSettings> = changes.map { get(appId) }

    /** The eye height to give a game: its own height if it has one, else the defaults'. */
    fun eyeHeightCm(appId: Int): Int = get(appId).eyeHeightCm(defaults.value)

    fun set(appId: Int, settings: GameSettings) {
        val editor = prefs.edit().putBoolean("seated_$appId", settings.seated)
        val height = settings.heightCm
        if (height == null) editor.remove("height_$appId") else editor.putInt("height_$appId", height.coerceIn(PlayerDefaults.HEIGHT_RANGE_CM))
        editor.apply()
        changes.value += 1
    }

    private fun readDefaults() = PlayerDefaults(prefs.getInt(KEY_DEFAULT_HEIGHT, PlayerDefaults.DEFAULT_HEIGHT_CM))

    private companion object {
        const val PREFS = "gameport_game_settings"
        const val KEY_DEFAULT_HEIGHT = "default_height"
    }
}
