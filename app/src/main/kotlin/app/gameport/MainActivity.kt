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

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                GamePortApp()
            }
        }
    }
}
