package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.AccentPresets
import app.gameport.core.model.AppLanguage
import app.gameport.core.model.CoverSize
import app.gameport.core.model.CoverSpacing
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.HoverAnimation
import app.gameport.core.model.PillSize
import app.gameport.core.model.LibrarySort
import app.gameport.core.model.ContinueScope
import app.gameport.core.model.SpeedUnit
import app.gameport.core.model.defaultSpeedUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Preferences the user can change; kept on the device, shared by every screen. */
@Singleton
class UserSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _speedUnit = MutableStateFlow(readSpeedUnit())

    val speedUnit: StateFlow<SpeedUnit> = _speedUnit.asStateFlow()

    fun setSpeedUnit(unit: SpeedUnit) {
        prefs.edit().putString(KEY_SPEED_UNIT, unit.name).apply()
        _speedUnit.value = unit
    }

    private val _countPlaytimeOnSteam = MutableStateFlow(prefs.getBoolean(KEY_COUNT_PLAYTIME, true))

    /** Whether Steam is told a game is being played, so its time counts on the account; Steam allows one game at a time per account. */
    val countPlaytimeOnSteam: StateFlow<Boolean> = _countPlaytimeOnSteam.asStateFlow()

    fun setCountPlaytimeOnSteam(count: Boolean) {
        prefs.edit().putBoolean(KEY_COUNT_PLAYTIME, count).apply()
        _countPlaytimeOnSteam.value = count
    }

    private val _returnToGamePort = MutableStateFlow(prefs.getBoolean(KEY_RETURN, true))

    /** Whether a game started by GamePort opens GamePort again when it closes (Horizon would otherwise leave the player on its home). */
    val returnToGamePort: StateFlow<Boolean> = _returnToGamePort.asStateFlow()

    fun setReturnToGamePort(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RETURN, enabled).apply()
        _returnToGamePort.value = enabled
    }

    private val _updateCheckHours = MutableStateFlow(prefs.getInt(KEY_UPDATE_HOURS, DEFAULT_UPDATE_HOURS))

    /** Hours between two looks for a newer GamePort when it opens; 0 means never. Nothing is installed without a tap. */
    val updateCheckHours: StateFlow<Int> = _updateCheckHours.asStateFlow()

    fun setUpdateCheckHours(hours: Int) {
        prefs.edit().putInt(KEY_UPDATE_HOURS, hours).apply()
        _updateCheckHours.value = hours
    }

    private val _display = MutableStateFlow(readDisplay())

    /** How the app looks and what the library shows. */
    val display: StateFlow<DisplaySettings> = _display.asStateFlow()

    fun updateDisplay(change: (DisplaySettings) -> DisplaySettings) {
        val updated = change(_display.value)
        prefs.edit()
            .putBoolean(KEY_BACKDROP, updated.backdrop)
            .putInt(KEY_BACKDROP_STRENGTH, updated.backdropStrength)
            .putBoolean(KEY_COVER_TITLES, updated.coverTitles)
            .putString(KEY_COVER_SIZE, updated.coverSize.name)
            .putString(KEY_COVER_SPACING, updated.coverSpacing.name)
            .putString(KEY_HOVER, updated.hoverAnimation.name)
            .putLong(KEY_ACCENT, updated.accent)
            .putBoolean(KEY_HERO, updated.heroBanner)
            .putString(KEY_PILL_SIZE, updated.pillSize.name)
            .putLong(KEY_GRADIENT_START, updated.gradientStart)
            .putLong(KEY_GRADIENT_END, updated.gradientEnd)
            .putString(KEY_CONTINUE_SCOPE, updated.continueScope.name)
            .putBoolean(KEY_CONTINUE, updated.showContinue)
            .putBoolean(KEY_FAVORITES, updated.showFavorites)
            .putBoolean(KEY_HIDE_UNINSTALLED, updated.hideUninstalled)
            .putString(KEY_SORT, updated.sort.name)
            .apply()
        _display.update { updated }
    }

    private val _language = MutableStateFlow(AppLanguage.ofTag(prefs.getString(KEY_LANGUAGE, null)))

    /** The language of the app's texts; [AppLanguage.AUTO] follows the device. Applied when a screen is created ([AppLocale]). */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    private fun readDisplay(): DisplaySettings {
        val defaults = DisplaySettings()
        return DisplaySettings(
            backdrop = prefs.getBoolean(KEY_BACKDROP, defaults.backdrop),
            backdropStrength = prefs.getInt(KEY_BACKDROP_STRENGTH, defaults.backdropStrength).coerceIn(0, 100),
            coverTitles = prefs.getBoolean(KEY_COVER_TITLES, defaults.coverTitles),
            coverSize = enumOf(KEY_COVER_SIZE, defaults.coverSize),
            coverSpacing = enumOf(KEY_COVER_SPACING, defaults.coverSpacing),
            hoverAnimation = enumOf(KEY_HOVER, defaults.hoverAnimation),
            accent = runCatching { prefs.getLong(KEY_ACCENT, defaults.accent) }.getOrDefault(defaults.accent),
            heroBanner = prefs.getBoolean(KEY_HERO, defaults.heroBanner),
            pillSize = enumOf(KEY_PILL_SIZE, defaults.pillSize),
            gradientStart = runCatching { prefs.getLong(KEY_GRADIENT_START, defaults.gradientStart) }.getOrDefault(defaults.gradientStart),
            gradientEnd = runCatching { prefs.getLong(KEY_GRADIENT_END, defaults.gradientEnd) }.getOrDefault(defaults.gradientEnd),
            continueScope = enumOf(KEY_CONTINUE_SCOPE, defaults.continueScope),
            showContinue = prefs.getBoolean(KEY_CONTINUE, defaults.showContinue),
            showFavorites = prefs.getBoolean(KEY_FAVORITES, defaults.showFavorites),
            hideUninstalled = prefs.getBoolean(KEY_HIDE_UNINSTALLED, defaults.hideUninstalled),
            sort = enumOf(KEY_SORT, defaults.sort),
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, null).orEmpty()) }.getOrDefault(default)

    private fun readSpeedUnit(): SpeedUnit =
        runCatching { SpeedUnit.valueOf(prefs.getString(KEY_SPEED_UNIT, null).orEmpty()) }
            .getOrDefault(defaultSpeedUnit(Locale.getDefault().toLanguageTag()))

    internal companion object {
        const val PREFS = "gameport_settings"
        const val KEY_SPEED_UNIT = "speed_unit"
        const val KEY_LANGUAGE = "language"
        const val KEY_UPDATE_HOURS = "update_check_hours"
        const val DEFAULT_UPDATE_HOURS = 4
        const val KEY_RETURN = "return_to_gameport"
        const val KEY_COUNT_PLAYTIME = "count_playtime_on_steam"
        const val KEY_BACKDROP = "library_backdrop"
        const val KEY_BACKDROP_STRENGTH = "backdrop_strength"
        const val KEY_COVER_TITLES = "cover_titles"
        const val KEY_COVER_SIZE = "cover_size"
        const val KEY_COVER_SPACING = "cover_spacing"
        const val KEY_HOVER = "hover_animation"
        const val KEY_ACCENT = "accent_argb"
        const val KEY_HERO = "hero_title"
        const val KEY_PILL_SIZE = "pill_size"
        const val KEY_GRADIENT_START = "gradient_start"
        const val KEY_GRADIENT_END = "gradient_end"
        const val KEY_CONTINUE_SCOPE = "continue_scope"
        const val KEY_CONTINUE = "home_continue"
        const val KEY_FAVORITES = "home_favorites"
        const val KEY_HIDE_UNINSTALLED = "hide_uninstalled"
        const val KEY_SORT = "library_sort"
    }
}
