package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.Glyph
import app.gameport.core.designsystem.glass
import app.gameport.core.model.ControllerAnswer

/** What the card that asks about the controls does with the player's choice, and whether it is shown. */
data class ControllerFeedbackAsk(
    val visible: Boolean = false,
    val onAnswer: (ControllerAnswer) -> Unit = {},
    val onLater: () -> Unit = {},
)

private val FeedbackGreen = Color(0xFF66BB6A)
private val FeedbackOrange = Color(0xFFFFB74D)
private val FeedbackRed = Color(0xFFE57373)

/** The colour and the mark of an answer: the ones of the games' labels, the same everywhere the answers are given. */
internal fun ControllerAnswer.tint(): Color = when (this) {
    ControllerAnswer.WORKS -> FeedbackGreen
    ControllerAnswer.PARTIAL -> FeedbackOrange
    ControllerAnswer.FAILS -> FeedbackRed
}

internal fun ControllerAnswer.glyph(): Glyph = when (this) {
    ControllerAnswer.WORKS -> Glyph.CHECK
    ControllerAnswer.PARTIAL -> Glyph.EXCLAMATION
    ControllerAnswer.FAILS -> Glyph.CROSS
}

@Composable
internal fun ControllerAnswer.label(): String = stringResource(
    when (this) {
        ControllerAnswer.WORKS -> R.string.controllers_feedback_yes
        ControllerAnswer.PARTIAL -> R.string.controllers_feedback_some
        ControllerAnswer.FAILS -> R.string.controllers_feedback_no
    },
)

/**
 * The card on a game's page that asks, after a run with the player's own mapping, whether the controls worked. It is a card of the page, not a window and not a
 * notification: it goes by itself once answered, and comes back only when the mapping changes (or after the next run, when "later" was chosen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControllerFeedbackCard(ask: ControllerFeedbackAsk, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(stringResource(R.string.controllers_feedback_card_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.controllers_feedback_card_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ControllerAnswer.entries.forEach { answer ->
                GlassButton(onClick = { ask.onAnswer(answer) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    CompatGlyph(answer.glyph(), answer.tint(), height = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(answer.label(), maxLines = 1)
                }
            }
            GlassButton(onClick = ask.onLater, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.controllers_feedback_later), maxLines = 1)
            }
        }
    }
}
