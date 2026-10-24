package app.gameport.whatsnew

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.gameport.R
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.model.AuthState
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Tells the player, once, which installed games may be missing their large files (see [app.gameport.core.model.ExpansionCheck]). Players of a game that did not start
 * cannot always be reached to be told to reinstall it, so the app says it itself, and each game's page then keeps a notice with the repair button.
 */
@HiltViewModel
class ExpansionNoticeViewModel @Inject constructor(
    private val settings: UserSettings,
    auth: SteamAuthRepository,
    installer: GameInstallRepository,
    library: SteamLibraryRepository,
) : ViewModel() {
    /** The names of the games to tell about, or empty when there is nothing to say (or it was said). */
    val games: StateFlow<List<String>> = combine(auth.authState, settings.expansionNoticeSeen, installer.expansionMayBeMissing, library.observeLibrary()) { signedIn, seen, ids, loaded ->
        if (signedIn !is AuthState.SignedIn || seen) emptyList()
        else loaded.games.filter { it.appId in ids }.map { it.name }.sortedBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onUnderstood() = settings.markExpansionNoticeSeen()
}

@Composable
fun ExpansionNoticeHost(viewModel: ExpansionNoticeViewModel = hiltViewModel()) {
    val games = viewModel.games.collectAsStateWithLifecycle().value
    if (games.isEmpty()) return
    BackdropDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.expansion_notice_title)) },
        text = { Text(stringResource(R.string.expansion_notice_text, games.joinToString("\n") { "• $it" })) },
        confirmButton = { Button(onClick = viewModel::onUnderstood) { Text(stringResource(R.string.expansion_notice_ok)) } },
    )
}
