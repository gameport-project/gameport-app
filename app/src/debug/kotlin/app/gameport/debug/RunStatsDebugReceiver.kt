package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.RunStats
import app.gameport.core.sync.ReportStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.RunStatsDebugReceiver --ei appId <id> --es values "<13 numbers>" --ei seconds <how long the run lasted>
 * Test only. Writes what a run measured for an installed game, as the hook would have handed it over, and ends a run of that length, so the card of the last run and the advice on the
 * resolution can be seen on the game's page without playing. The 13 numbers are those of [RunStats]: the recommended width and height, the image width and height, the median frame rate, the
 * slowest tenth, how many seconds of it, the target, the longest slow stretch, the memory peak, the least free memory, the start in seconds, and when the hook last handed over.
 * Example, the report that led to the advice: values "2800 2933 0 0 1 1 2 72 2 825 1680 3 6" seconds 41 (the whole command in quotes for the shell of the device).
 * With --ez clear true, the measures of the game are forgotten instead.
 */
@AndroidEntryPoint
class RunStatsDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: GameInstallRepository
    @Inject lateinit var reports: ReportStore

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getIntExtra("appId", 0)
        val packageName = installer.installedPackage(appId)
        if (packageName == null) {
            Log.i(TAG, "app $appId: not installed")
            return
        }
        if (intent.getBooleanExtra("clear", false)) {
            reports.forgetRun(packageName)
            Log.i(TAG, "app $appId: run measures forgotten")
            return
        }
        val stats = RunStats.parse("1 " + intent.getStringExtra("values").orEmpty().trim())
        if (stats == null) {
            Log.i(TAG, "app $appId: values must be ${RunStats.FIELDS} numbers")
            return
        }
        reports.noteRun(packageName, stats)
        val seconds = intent.getIntExtra("seconds", 0)
        if (seconds > 0) {
            val now = System.currentTimeMillis()
            reports.launched(packageName, pid = (now % 100000).toInt(), nowMillis = now - seconds * 1000L)
            reports.left(packageName, now)
        }
        Log.i(TAG, "app $appId: run measures written, run of $seconds s")
    }

    private companion object {
        const val TAG = "GPRunStats"
    }
}
