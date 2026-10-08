package app.gameport.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.gameport.R
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.dialogMaxHeight
import app.gameport.core.model.AuthState
import app.gameport.core.model.IncompatibleReason
import app.gameport.core.model.pick
import app.gameport.core.settings.IncompatibleGames
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A game of the library that is confirmed as incompatible, and why. */
data class IncompatibleNotice(val appId: Int, val name: String, val reason: IncompatibleReason?)

/**
 * Tells the player, once for each game, that a game of the library is confirmed as incompatible and is hidden. The games told are kept when
 * the player presses "got it", so a game never comes back in this window. Nothing is told while the player shows these games anyway.
 */
@HiltViewModel
class IncompatibleNoticeViewModel @Inject constructor(
    auth: SteamAuthRepository,
    library: SteamLibraryRepository,
    private val incompatible: IncompatibleGames,
) : ViewModel() {
    val notices: StateFlow<List<IncompatibleNotice>> = combine(
        auth.authState, library.observeLibrary(), incompatible.acknowledged, incompatible.showAnyway,
    ) { authState, loaded, told, showAnyway ->
        if (authState !is AuthState.SignedIn || showAnyway) emptyList()
        else loaded.games
            .filter { incompatible.list.game(it.appId) != null && it.appId !in told }
            .sortedBy { it.name.lowercase() }
            .map { IncompatibleNotice(it.appId, it.name, incompatible.list.reasonOf(it.appId)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onGotIt() = incompatible.acknowledge(notices.value.map { it.appId })
}

/** The window that tells which games of the library are confirmed as incompatible. It cannot be closed other than by "got it". */
@Composable
fun IncompatibleNoticeHost(viewModel: IncompatibleNoticeViewModel = hiltViewModel()) {
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    if (notices.isEmpty()) {
        // Nothing to tell about incompatible games: the last window of the chain may ask whether a game worked.
        VerdictAskHost()
        return
    }
    val language = LocalConfiguration.current.locales[0].language
    BackdropDialog(
        onDismissRequest = {},
        maxHeight = dialogMaxHeight(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.incompatible_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pluralStringResource(R.plurals.incompatible_intro, notices.size, notices.size))
                notices.forEach { notice ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(notice.name, style = MaterialTheme.typography.titleMedium)
                        notice.reason?.let { reason ->
                            // The reason runs on from its title, lighter than the name of the game, so the two are not mistaken for each other.
                            Text(reasonText(reason.title.pick(language), reason.separator(language), reason.text.pick(language), MaterialTheme.colorScheme.primary), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GlassButton(onClick = viewModel::onGotIt) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.whatsnew_got_it))
                }
            }
        },
    )
}

/** The title of a reason in colour, then its text on the same line after a colon. */
private fun reasonText(title: String, separator: String, text: String, color: Color) = buildAnnotatedString {
    withStyle(SpanStyle(color = color)) { append(title) }
    append(separator)
    append(text)
}
