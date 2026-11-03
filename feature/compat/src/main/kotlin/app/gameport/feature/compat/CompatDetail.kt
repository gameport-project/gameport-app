package app.gameport.feature.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.symptomLabel
import app.gameport.core.model.CompatCounts
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.CompatReport
import app.gameport.core.model.CompatRow
import app.gameport.core.model.CompatRows
import app.gameport.core.model.CompatTurn
import app.gameport.core.model.Verdict

private val IncompatibleRed = Color(0xFFE57373)

/**
 * A game's label opened up: why it is known not to run when it is, then what the players reported, one line for each model of device, answer and version of GamePort
 * ("3 players, Quest 3, GamePort 0.8.2, compatible"), the lines of the device in hand first. [reports] are the lines the relay lists; until they are read, the totals of
 * each model in [counts] give the same lines without a version. [deviceModel] is the name of the device when it is known (Quest 3, Pico 4).
 */
@Composable
fun CompatDetailDialog(
    gameName: String,
    level: CompatLevel?,
    counts: CompatCounts?,
    reports: List<CompatReport>?,
    turn: CompatTurn?,
    deviceKind: String,
    deviceModel: String?,
    appVersion: String,
    incompatibleTitle: String?,
    incompatibleText: String?,
    onDismiss: () -> Unit,
) {
    val rows = CompatRows.of(reports, counts, deviceKind, deviceModel)
    val shownTurn = turn ?: counts?.devices?.get(deviceKind)?.turn
    BackdropDialog(
        onDismissRequest = onDismiss,
        title = { Text(gameName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (incompatibleTitle != null && incompatibleText != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(incompatibleTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = IncompatibleRed)
                        Text(incompatibleText.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (level != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow { VerdictChip(level, deviceKind) }
                        Text(
                            stringResource(R.string.compat_detail_device_line, deviceModel ?: deviceName(deviceKind), appVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        turnWords(shownTurn)?.let { Text(it.first, style = MaterialTheme.typography.labelMedium, color = it.second) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.compat_detail_players), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (rows.isEmpty()) {
                        Text(stringResource(R.string.compat_detail_nobody), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        rows.forEach { ReportRow(it) }
                    }
                }
            }
        },
        confirmButton = {
            GlassButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.compat_detail_close))
            }
        },
    )
}

/** The level a player's answer stands for: its mark and its colour are the ones of the labels. */
private fun levelOf(verdict: Verdict): CompatLevel = when (verdict) {
    Verdict.WORKS -> CompatLevel.WORKS
    Verdict.OFFLINE_ONLY -> CompatLevel.OFFLINE_ONLY
    Verdict.FAILS -> CompatLevel.FAILS
}

/** One line: the mark of the answer, the model and the version of GamePort, then the answer in its colour and how many players gave it. The device in hand has an outline. */
@Composable
private fun ReportRow(row: CompatRow) {
    val level = levelOf(row.verdict)
    val (colour, _, _) = mark(level)
    val shape = RoundedCornerShape(12.dp)
    val words = stringResource(
        when (row.verdict) {
            Verdict.WORKS -> R.string.compat_row_works
            Verdict.OFFLINE_ONLY -> R.string.compat_row_offline_only
            Verdict.FAILS -> R.string.compat_row_fails
        },
    )
    // What goes under the line: what the players ticked as going wrong, and the version from which it may be solved.
    val symptoms = row.symptoms.take(MAX_SYMPTOMS).map { symptomLabel(it).replaceFirstChar { c -> c.lowercase() } }
    val maybeFixed = row.fixedSince?.let { stringResource(R.string.compat_row_maybe_fixed, it) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = if (row.mine) 0.10f else 0.05f), shape)
            .border(1.dp, if (row.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f), shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompatMark(level, 22.dp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name ?: deviceName(row.kind),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.mine) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            row.version?.let {
                Text(stringResource(R.string.compat_row_version, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(words, style = MaterialTheme.typography.labelLarge, color = colour, maxLines = 1)
        Text(
            pluralStringResource(R.plurals.compat_row_players, row.count, row.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    if (symptoms.isNotEmpty() || maybeFixed != null) {
        Row(Modifier.padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (symptoms.isNotEmpty()) Text(symptoms.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (maybeFixed != null) Text(maybeFixed, style = MaterialTheme.typography.labelSmall, color = FixedGreen)
        }
    }
    }
}

/** How many of the symptoms ticked for a line are told: the most said ones, so a line stays one or two lines high. */
private const val MAX_SYMPTOMS = 3

private val FixedGreen = Color(0xFF66BB6A)

/** The turn of the game for a kind of device in a few words and the colour that goes with it, or null when the history says nothing. */
@Composable
private fun turnWords(turn: CompatTurn?): Pair<String, Color>? {
    val fixedIn = turn?.fixedIn
    val stillIn = turn?.stillIn
    return when {
        turn == null -> null
        turn.regression -> stringResource(R.string.compat_turn_broke, turn.brokeIn.orEmpty()) to IncompatibleRed
        fixedIn != null && stillIn != null -> stringResource(R.string.compat_turn_fixed_still, fixedIn, stillIn) to FixedGreen
        fixedIn != null -> stringResource(R.string.compat_turn_fixed, fixedIn) to FixedGreen
        else -> null
    }
}
