package app.gameport.feature.game

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.GlassButton

internal fun workshopUrl(appId: Int) = "https://steamcommunity.com/app/$appId/workshop/"

/**
 * The Workshop of a game: Steam's own page, with the account already signed in (no new sign-in, see [WorkshopViewModel]), in the browser GamePort has for the pages of Steam
 * ([SteamBrowser]). The button at the top right gets back to the game without leaving GamePort: the game is started again, or brought to the front when it is still running.
 */
@Composable
fun WorkshopScreen(onBack: () -> Unit, viewModel: WorkshopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.gameName.collectAsStateWithLifecycle()
    val running by viewModel.gameRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SteamBrowser(
        title = stringResource(R.string.workshop_title),
        subtitle = name,
        hint = stringResource(R.string.workshop_hint),
        startUrl = workshopUrl(viewModel.appId),
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
        action = {
            val toGame = { viewModel.launchIntent()?.let { runCatching { context.startActivity(it) } }; Unit }
            // A game still there is gone back to (glass button); one that is not is launched (the filled button, as on the game's page).
            if (running) {
                GlassButton(onClick = toGame) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.workshop_back_to_game))
                }
            } else {
                Button(onClick = toGame) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.workshop_launch_game))
                }
            }
        },
    )
}
