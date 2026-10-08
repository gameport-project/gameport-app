package app.gameport.whatsnew

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.gameport.R
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.Glyph
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.designsystem.dialogMaxHeight
import app.gameport.core.model.AuthState
import app.gameport.core.model.Verdict
import app.gameport.core.settings.GameVerdicts
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The game the player is asked about, and whether the answer will be sent to the project. */
data class VerdictQuestion(val appId: Int, val name: String, val shared: Boolean, val cover: String, val coverFallbacks: List<String>)

/**
 * Asks, once a game has closed, whether it worked. The game waits in [GameVerdicts] (see `VerdictAsker`) until GamePort is on screen and the
 * other windows are closed. Only a game of the library can be asked about, so its name can be shown.
 */
@HiltViewModel
class VerdictAskViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    auth: SteamAuthRepository,
    library: SteamLibraryRepository,
    private val verdicts: GameVerdicts,
) : ViewModel() {
    private val version: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    private val today: String get() = LocalDate.now().toString()

    val question: StateFlow<VerdictQuestion?> = combine(auth.authState, library.observeLibrary(), verdicts.book, verdicts.share) { authState, loaded, book, share ->
        if (authState !is AuthState.SignedIn) null
        else loaded.games.filter { it.appId in book.pending }.minByOrNull { it.name.lowercase() }?.let { VerdictQuestion(it.appId, it.name, share, it.capsuleUrl, it.capsuleFallbacks) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onAnswer(verdict: Verdict) {
        val asked = question.value ?: return
        verdicts.answer(asked.appId, verdict, version, today)
    }

    fun onLater() {
        val asked = question.value ?: return
        verdicts.update { it.dismissed(asked.appId, version, today) }
    }
}

/** The question "did it work?", as a window: it is closed by one of its three buttons, never by a press outside or by Back. */
@Composable
fun VerdictAskHost(viewModel: VerdictAskViewModel = hiltViewModel()) {
    val asked by viewModel.question.collectAsStateWithLifecycle()
    val question = asked ?: return
    BackdropDialog(
        onDismissRequest = {},
        maxHeight = dialogMaxHeight(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = KindBlue, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.verdict_title, question.name))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameImage(
                        url = question.cover,
                        fallbackUrl = question.coverFallbacks.firstOrNull(),
                        moreFallbacks = question.coverFallbacks.drop(1),
                        contentDescription = null,
                        modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (question.shared) R.string.verdict_shared else R.string.verdict_kept))
                        Text(
                            stringResource(R.string.verdict_where),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            // The two main answers share the first line, whole; the answer "offline only" and "not now" share the second, so no label is cut.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Answer(R.string.verdict_worked, glyph = Glyph.CHECK, tint = WorksGreen) { viewModel.onAnswer(Verdict.WORKS) }
                    Answer(R.string.verdict_failed, glyph = Glyph.CROSS, tint = FailsRed) { viewModel.onAnswer(Verdict.FAILS) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Answer(R.string.verdict_offline_only, glyph = Glyph.NO_NETWORK, tint = OfflineOrange) { viewModel.onAnswer(Verdict.OFFLINE_ONLY) }
                    Answer(R.string.verdict_later, icon = Icons.Filled.Schedule, onClick = viewModel::onLater)
                }
            }
        },
    )
}

@Composable
private fun RowScope.Answer(text: Int, glyph: Glyph? = null, icon: ImageVector? = null, tint: Color? = null, onClick: () -> Unit) {
    GlassButton(onClick = onClick, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        // The marks are the ones of the labels of the games; only they are in colour, the buttons are the same as everywhere else.
        if (glyph != null && tint != null) CompatGlyph(glyph, tint, height = 14.dp) else if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        // The label may take two lines rather than be cut.
        Text(stringResource(text), maxLines = 2)
    }
}

private val WorksGreen = Color(0xFF66BB6A)
private val FailsRed = Color(0xFFE57373)
private val OfflineOrange = Color(0xFFFFB74D)
