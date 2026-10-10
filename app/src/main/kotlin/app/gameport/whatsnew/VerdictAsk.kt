package app.gameport.whatsnew

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import app.gameport.core.sync.ProblemReporter
import app.gameport.core.sync.ReportSender
import app.gameport.core.sync.ReportStatus
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The game the player is asked about, and whether the answer will be sent to the project. */
data class VerdictQuestion(val appId: Int, val name: String, val shared: Boolean, val cover: String, val coverFallbacks: List<String>)

/**
 * The window that follows the answer "it did not work": what became of the technical report, and the two ways to take it. [status] is null while the
 * report is being made. [busy] is true while the player's own request is being dealt with.
 */
data class ReportFollowUp(val appId: Int, val status: ReportStatus?, val busy: Boolean = false, val savedName: String? = null, val saveFailed: Boolean = false, val offlineOnly: Boolean = false)

/**
 * Asks, once a game has closed, whether it worked. The game waits in [GameVerdicts] (see `VerdictAsker`) until GamePort is on screen and the
 * other windows are closed. Only a game of the library can be asked about, so its name can be shown.
 */
@HiltViewModel
class VerdictAskViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    auth: SteamAuthRepository,
    private val library: SteamLibraryRepository,
    private val verdicts: GameVerdicts,
    private val reports: ReportSender,
    private val reporter: ProblemReporter,
) : ViewModel() {
    private val version: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    private val today: String get() = LocalDate.now().toString()

    val question: StateFlow<VerdictQuestion?> = combine(auth.authState, library.observeLibrary(), verdicts.book, verdicts.share) { authState, loaded, book, share ->
        if (authState !is AuthState.SignedIn) null
        else loaded.games.filter { it.appId in book.pending }.minByOrNull { it.name.lowercase() }?.let { VerdictQuestion(it.appId, it.name, share, it.capsuleUrl, it.capsuleFallbacks) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _followUp = MutableStateFlow<ReportFollowUp?>(null)
    val followUp: StateFlow<ReportFollowUp?> = _followUp.asStateFlow()

    fun onAnswer(verdict: Verdict) {
        val asked = question.value ?: return
        verdicts.answer(asked.appId, verdict, version, today)
        // A game that did not work, or whose online part does not, leaves something to look at in the report.
        if (verdict == Verdict.FAILS || verdict == Verdict.OFFLINE_ONLY) startReport(asked.appId, offlineOnly = verdict == Verdict.OFFLINE_ONLY)
    }

    /** "It did not work", or "works offline only": the report is made from the run that just ended and goes by itself when sending is on and the device has a network. */
    private fun startReport(appId: Int, offlineOnly: Boolean) {
        _followUp.value = ReportFollowUp(appId, status = null, offlineOnly = offlineOnly)
        viewModelScope.launch {
            val status = gameOf(appId)?.let { reports.sendAfterFailure(it) } ?: ReportStatus.UNAVAILABLE
            _followUp.update { it?.copy(status = status) }
        }
    }

    private suspend fun gameOf(appId: Int) = library.observeLibrary().first().games.firstOrNull { it.appId == appId }

    fun onSendReport() {
        val current = _followUp.value?.takeIf { !it.busy } ?: return
        _followUp.value = current.copy(busy = true)
        viewModelScope.launch {
            val status = reports.sendNow(current.appId)
            _followUp.update { it?.copy(status = status, busy = false) }
        }
    }

    fun onDownloadReport() {
        val current = _followUp.value?.takeIf { !it.busy } ?: return
        _followUp.value = current.copy(busy = true, saveFailed = false)
        viewModelScope.launch {
            val saved = gameOf(current.appId)?.let { reporter.save(it) }
            _followUp.update { it?.copy(busy = false, savedName = saved?.fileName, saveFailed = saved == null) }
        }
    }

    fun onCloseReport() {
        _followUp.value = null
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
    val followUp by viewModel.followUp.collectAsStateWithLifecycle()
    followUp?.let { ReportFollowUpDialog(it, viewModel::onDownloadReport, viewModel::onSendReport, viewModel::onCloseReport) }
    // The question waits behind the window that follows an answer: only one of the two is on screen.
    if (followUp != null) return
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
            // The four buttons are stacked, each as wide as the window, so no label is cut or wraps: the three answers, then "not now" in white.
            // A button reserves an invisible margin to reach 48 dp of touch area, which adds to the space between them: it is taken away here, so the
            // space between the buttons is the one that is written.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Answer(R.string.verdict_worked, glyph = Glyph.CHECK, tint = WorksGreen) { viewModel.onAnswer(Verdict.WORKS) }
                    Answer(R.string.verdict_offline_only, glyph = Glyph.NO_NETWORK, tint = OfflineOrange) { viewModel.onAnswer(Verdict.OFFLINE_ONLY) }
                    Answer(R.string.verdict_failed, glyph = Glyph.CROSS, tint = FailsRed) { viewModel.onAnswer(Verdict.FAILS) }
                    NotNow(viewModel::onLater)
                }
            }
        },
    )
}

/** After "it did not work": the report that goes with the answer, what became of it, and the two ways to take it by hand. Closed by its button only. */
@Composable
private fun ReportFollowUpDialog(state: ReportFollowUp, onDownload: () -> Unit, onSend: () -> Unit, onClose: () -> Unit) {
    val ready = state.status != null && state.status != ReportStatus.UNAVAILABLE
    BackdropDialog(
        onDismissRequest = {},
        maxHeight = dialogMaxHeight(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = KindBlue, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.report_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (state.offlineOnly) R.string.report_intro_offline else R.string.report_intro))
                val (message, color) = when (state.status) {
                    null -> R.string.report_status_working to MaterialTheme.colorScheme.onSurfaceVariant
                    ReportStatus.SENT -> R.string.report_status_sent to WorksGreen
                    ReportStatus.WAITING -> R.string.report_status_waiting to MaterialTheme.colorScheme.onSurfaceVariant
                    ReportStatus.OFF -> R.string.report_status_off to MaterialTheme.colorScheme.onSurfaceVariant
                    ReportStatus.REFUSED -> R.string.report_status_refused to FailsRed
                    ReportStatus.UNAVAILABLE -> R.string.report_status_unavailable to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(stringResource(message), color = color)
                state.savedName?.let { Text(stringResource(R.string.report_saved, it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                if (state.saveFailed) Text(stringResource(R.string.report_save_failed), color = FailsRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ready) {
                        GlassButton(onClick = onDownload, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.report_download), maxLines = 1)
                        }
                        // Not offered once the relay has it.
                        if (state.status != ReportStatus.SENT) {
                            GlassButton(onClick = onSend, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.report_send), maxLines = 1)
                            }
                        }
                    }
                    CloseButton(onClose)
                }
            }
        },
    )
}

/** The way out, in white like "not now" in the question. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.report_close), maxLines = 1)
    }
}

@Composable
private fun Answer(text: Int, glyph: Glyph, tint: Color, onClick: () -> Unit) {
    GlassButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        // The marks are the ones of the labels of the games; only they are in colour, the buttons are the same as everywhere else.
        CompatGlyph(glyph, tint, height = 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(text), maxLines = 1)
    }
}

/** The way out, in white: a plain filled button, so it stands apart from the three answers. */
@Composable
private fun NotNow(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.verdict_later), maxLines = 1)
    }
}

private val WorksGreen = Color(0xFF66BB6A)
private val FailsRed = Color(0xFFE57373)
private val OfflineOrange = Color(0xFFFFB74D)
