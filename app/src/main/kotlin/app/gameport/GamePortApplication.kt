package app.gameport

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GamePortApplication : Application() {
    @Inject lateinit var achievements: app.gameport.core.sync.AchievementNotifier

    override fun onCreate() {
        super.onCreate()
        // A headset stops GamePort when a game is left, which removes its notifications: the achievements not yet removed come back.
        achievements.restore()
    }
}
