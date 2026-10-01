package app.gameport.core.settings

import android.content.Context
import android.content.res.Configuration
import app.gameport.core.model.AppLanguage
import java.util.Locale

/** Gives a screen the language chosen in the settings; the device's own when the choice is automatic. */
object AppLocale {
    /** Meant for `attachBaseContext` of each activity: read straight from the preferences, as nothing is injected yet. */
    fun wrap(base: Context): Context {
        val tag = base.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE).getString(UserSettings.KEY_LANGUAGE, null)
        if (AppLanguage.ofTag(tag) == AppLanguage.AUTO) return base
        val locale = Locale.forLanguageTag(tag.orEmpty())
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }
}
