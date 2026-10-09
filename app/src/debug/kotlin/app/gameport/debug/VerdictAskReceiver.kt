package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.gameport.core.settings.GameVerdicts
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.VerdictAskReceiver --ei appId <steam app id>
 * Test only. Makes the question "did it work?" wait for that game of the library, as if it had just closed, so it can be seen without playing.
 * `--ez open true` instead tries to open GamePort from the background (to find out whether Android lets it).
 * `--ez forget true` also forgets what was answered for it, so the question is asked as the first time.
 */
@AndroidEntryPoint
class VerdictAskReceiver : BroadcastReceiver() {
    @Inject lateinit var verdicts: GameVerdicts

    override fun onReceive(context: Context, intent: Intent) {
        // `--ez open true`: tries to bring GamePort to the screen from the background, as it would when a game ends, and says in the log what Android did.
        if (intent.getBooleanExtra("open", false)) {
            val result = runCatching {
                context.startActivity(Intent().setClassName(context, "app.gameport.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            android.util.Log.i("GPVerdict", "opening GamePort from the background: " + if (result.isSuccess) "asked" else "refused: ${result.exceptionOrNull()}")
            return
        }
        val appId = intent.getIntExtra("appId", 0).takeIf { it > 0 } ?: return
        val forget = intent.getBooleanExtra("forget", false)
        verdicts.update { book ->
            val base = if (forget) book.copy(games = book.games - appId) else book
            base.copy(pending = base.pending + appId)
        }
    }
}
