package app.gameport.core.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors

/** The player removed the notification of an achievement, or the group of a game: it is not brought back when GamePort starts again. */
class AchievementDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifier = EntryPointAccessors.fromApplication(context.applicationContext, CloudEntryPoint::class.java).achievementNotifier()
        intent.getStringExtra(AchievementNotifier.EXTRA_OPENED)?.let(notifier::forget)
        intent.getIntExtra(AchievementNotifier.EXTRA_GAME, 0).takeIf { it != 0 }?.let(notifier::forgetGame)
    }
}
