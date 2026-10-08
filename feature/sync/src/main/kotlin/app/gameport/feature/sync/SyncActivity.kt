package app.gameport.feature.sync

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.gameport.core.designsystem.GamePortTheme
import app.gameport.core.settings.AppLocale
import dagger.hilt.android.AndroidEntryPoint

/** In front of a launching game while its saves and Steam's disagree, or while another device plays with the account; closes once the player has answered. */
@AndroidEntryPoint
class SyncActivity : ComponentActivity() {
    private val viewModel: SyncViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GamePortTheme {
                val conflict by viewModel.conflict.collectAsState()
                val playChoice by viewModel.playChoice.collectAsState()
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(conflict, playChoice) {
                    if (conflict != null || playChoice != null) seen = true else if (seen) finish()
                }
                playChoice?.let {
                    PlayChoiceScreen(pending = it, onQuit = viewModel::onQuit, onKick = viewModel::onKick, onPlay = viewModel::onPlayAnyway)
                } ?: conflict?.let {
                    ConflictScreen(
                        conflict = it,
                        onKeepLocal = viewModel::onKeepLocal,
                        onUseCloud = viewModel::onUseCloud,
                        onDecideLater = viewModel::onDecideLater,
                    )
                }
            }
        }
    }
}
