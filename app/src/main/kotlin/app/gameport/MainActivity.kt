package app.gameport

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.gameport.core.designsystem.GamePortTheme
import app.gameport.core.settings.AppLocale
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import app.gameport.core.settings.UserSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: UserSettings

    @Inject lateinit var appUpdater: app.gameport.core.install.AppUpdater

    @Inject lateinit var logRetention: app.gameport.core.sync.LogRetention

    @Inject lateinit var device: app.gameport.core.device.DeviceProfile

    @Inject lateinit var achievements: app.gameport.core.sync.AchievementNotifier

    /** The game whose Workshop page was asked for from outside (a game's "browse" button), until the screen has opened it. */
    private val workshopRequest = androidx.compose.runtime.mutableStateOf<Int?>(null)

    /** The mock-up of a game's page, asked for with `--ez mockup true` and only by a debug build. */
    private val mockupRequest = androidx.compose.runtime.mutableStateOf(false)

    private fun wantsMockup(intent: android.content.Intent?) =
        intent?.getBooleanExtra("mockup", false) == true && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acknowledgeAchievement(intent)
        // A turn of the screen starts the activity again with the same intent: the page is not opened a second time.
        if (savedInstanceState == null) workshopRequest.value = workshopAppId(intent)
        // (Read every time: a phone turns the screen at start-up, which starts the activity again, and the page asked for must survive it.)
        mockupRequest.value = wantsMockup(intent)
        // Looks for a newer GamePort when the last look is old enough (and the player has not turned it off).
        appUpdater.checkIfDue()
        // Logs kept for problem reports: a week at most, and not for a game that was uninstalled.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { logRetention.run() }
        // The manifest starts every device in landscape, which is what a headset's panel needs (it cuts a portrait one off);
        // a phone or a tablet is then let turn as it is held.
        if (!device.isHeadset) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        enableEdgeToEdge()
        setContent {
            val display by settings.display.collectAsState()
            GamePortTheme(accent = Color(display.accent), gradientStart = Color(display.gradientStart), gradientEnd = Color(display.gradientEnd)) {
                GamePortApp(
                    openWorkshop = workshopRequest.value, onWorkshopOpened = { workshopRequest.value = null },
                    openMockup = mockupRequest.value, onMockupOpened = { mockupRequest.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        acknowledgeAchievement(intent)
        workshopAppId(intent)?.let { workshopRequest.value = it }
        if (wantsMockup(intent)) mockupRequest.value = true
    }

    /** GamePort was opened from the notification of an achievement: it is not brought back any more. */
    private fun acknowledgeAchievement(intent: android.content.Intent?) {
        intent?.getStringExtra(app.gameport.core.sync.AchievementNotifier.EXTRA_OPENED)?.let(achievements::opened)
    }
}
