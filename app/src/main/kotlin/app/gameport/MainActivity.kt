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

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acknowledgeAchievement(intent)
        // Looks for a newer GamePort when the last look is old enough (and the player has not turned it off).
        appUpdater.checkIfDue()
        // Logs kept for problem reports: a week at most, and not for a game that was uninstalled.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { logRetention.run() }
        askForStorageBelowAndroid11()
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        acknowledgeAchievement(intent)
    }

    /**
     * Asks for the storage permission on Android 10 and earlier, where a game's expansion files can only be placed with it: there is no
     * special access to all files below Android 11, so nothing else grants it, and an install that reaches the end without it has to be
     * done again. Asked once when GamePort opens, rather than in the middle of an install that has already downloaded gigabytes.
     * Android 11 and later never see it (the manifest caps the permission) and are asked for the special access only when it is needed.
     */
    private fun askForStorageBelowAndroid11() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) return
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        // A player who said no is not asked again at every start: the install that needs it offers the page instead.
        runCatching { requestPermissions(arrayOf(permission), STORAGE_REQUEST) }
    }

    /** GamePort was opened from the notification of an achievement: it is not brought back any more. */
    private fun acknowledgeAchievement(intent: android.content.Intent?) {
        intent?.getStringExtra(app.gameport.core.sync.AchievementNotifier.EXTRA_OPENED)?.let(achievements::opened)
    }

    private companion object {
        const val STORAGE_REQUEST = 1
    }
}
