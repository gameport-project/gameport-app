package app.gameport

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GamePortApplication : Application() {
    @Inject lateinit var achievements: app.gameport.core.sync.AchievementNotifier
    @Inject lateinit var steamAchievements: app.gameport.core.sync.SteamAchievementSync
    @Inject lateinit var saveCatchUp: app.gameport.core.sync.SaveCatchUp

    override fun onCreate() {
        super.onCreate()
        // A headset stops GamePort when a game is left, which removes its notifications: the achievements not yet removed come back.
        achievements.restore()
        // Achievements waiting to be added to the Steam account are sent as soon as Steam can be reached.
        steamAchievements.start()
        // Saves of a game played without a connection are sent once Steam can be reached again.
        saveCatchUp.start()
    }
}
