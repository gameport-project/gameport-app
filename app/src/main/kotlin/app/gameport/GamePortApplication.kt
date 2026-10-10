package app.gameport

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GamePortApplication : Application() {
    @Inject lateinit var achievements: app.gameport.core.sync.AchievementNotifier
    @Inject lateinit var steamAchievements: app.gameport.core.sync.SteamAchievementSync
    @Inject lateinit var saveCatchUp: app.gameport.core.sync.SaveCatchUp
    @Inject lateinit var connectionKeeper: app.gameport.core.sync.ConnectionKeeper
    @Inject lateinit var verdictSender: app.gameport.core.sync.VerdictSender
    @Inject lateinit var reportSender: app.gameport.core.sync.ReportSender
    @Inject lateinit var compat: app.gameport.core.sync.CompatRepository
    @Inject lateinit var verdictAsker: app.gameport.core.sync.VerdictAsker

    override fun onCreate() {
        super.onCreate()
        // A headset stops GamePort when a game is left, which removes its notifications: the achievements not yet removed come back.
        achievements.restore()
        // Achievements waiting to be added to the Steam account are sent as soon as Steam can be reached.
        steamAchievements.start()
        // Saves of a game played without a connection are sent once Steam can be reached again.
        saveCatchUp.start()
        // Quitting GamePort closes its connection to Steam too (see [ConnectionKeeper]).
        connectionKeeper.start()
        // The answers the player gave about games are sent to the relay of the project (if they chose to share them).
        verdictSender.start()
        reportSender.start()
        // What the players say about the games is read when GamePort starts (and when a page opens, if what is kept is old).
        compat.start()
        // The end of a game is also seen from the follow-up of the time played, for a game the system kills without it saying so.
        verdictAsker.start()
        // A game killed while GamePort was not looking (a minute or a day ago) is found out now.
        verdictAsker.checkRuns()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                connectionKeeper.screenShown()
                verdictAsker.checkRuns()
            }
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = connectionKeeper.screenOpened()
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) = connectionKeeper.screenClosed()
        })
    }
}
