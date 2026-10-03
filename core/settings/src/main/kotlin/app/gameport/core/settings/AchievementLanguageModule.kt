package app.gameport.core.settings

import app.gameport.core.model.AchievementLanguage
import app.gameport.core.model.SteamLanguage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Locale

/**
 * The achievements are asked from Steam in the language chosen in GamePort, so the page and the notification agree with the rest
 * of the app; the device's language when the choice is automatic. Dates are left to the device.
 */
@Module
@InstallIn(SingletonComponent::class)
object AchievementLanguageModule {
    @Provides
    fun achievementLanguage(settings: UserSettings): AchievementLanguage = AchievementLanguage {
        val tag = settings.language.value.tag
        if (tag != null) SteamLanguage.of(tag) else Locale.getDefault().let { SteamLanguage.of(it.language, it.country, it.script) }
    }
}
