package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.sync.PlayDecisions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.PlaySimulationReceiver --es pkg <package or all> --ez on true
 * Test only. Until turned off, the game (every game for "all") asks at every start what to do as if another device played with the account,
 * so the question can be seen and answered without one.
 */
@AndroidEntryPoint
class PlaySimulationReceiver : BroadcastReceiver() {
    @Inject lateinit var decisions: PlayDecisions

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("pkg") ?: return
        val on = intent.getBooleanExtra("on", true)
        decisions.simulate(pkg, on)
        Log.i("GPPlaySim", "$pkg: another device " + if (on) "is simulated" else "is no longer simulated")
    }
}
