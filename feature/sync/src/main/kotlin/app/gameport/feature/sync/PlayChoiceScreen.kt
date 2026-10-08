package app.gameport.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.sync.PlayDecisions

/**
 * In front of a launching game when Steam says another device plays with the account. Three ways out, side by side, each with what it comes to,
 * as Steam's own client asks: not to start, to stop the other device's game and play here, or to play without the Steam ticket.
 */
@Composable
internal fun PlayChoiceScreen(
    pending: PlayDecisions.Pending,
    onQuit: () -> Unit,
    onKick: () -> Unit,
    onPlay: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            modifier = Modifier.padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.play_title), style = MaterialTheme.typography.headlineMedium)
            Text(pending.gameName, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.play_explanation, pending.gameName),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 760.dp),
            )
            // The three cards have the same height, whatever their text, and their buttons are on the same line at the bottom.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Choice(stringResource(R.string.play_quit), stringResource(R.string.play_quit_detail), onQuit, filled = false, Modifier.weight(1f))
                Choice(stringResource(R.string.play_kick), stringResource(R.string.play_kick_detail, pending.gameName), onKick, filled = true, Modifier.weight(1f))
                Choice(stringResource(R.string.play_anyway), stringResource(R.string.play_anyway_detail, pending.gameName), onPlay, filled = false, Modifier.weight(1f))
            }
            Text(stringResource(R.string.play_once), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Choice(title: String, detail: String, onClick: () -> Unit, filled: Boolean, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().glass(RoundedCornerShape(18.dp)).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Column(Modifier.padding(top = 16.dp)) {
            if (filled) Button(onClick = onClick) { Text(stringResource(R.string.play_choose)) } else GlassButton(onClick = onClick) { Text(stringResource(R.string.play_choose)) }
        }
    }
}
