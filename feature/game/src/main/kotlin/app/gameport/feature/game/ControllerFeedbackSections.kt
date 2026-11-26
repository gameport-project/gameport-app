package app.gameport.feature.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.BackdropDialog
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.DangerTextButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.model.CommunityMapping
import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerAnswer
import app.gameport.core.model.ControllerFingerprint
import app.gameport.core.model.ControllerLayout
import app.gameport.core.model.ControllerMapping

/** What the page of the controllers shows about feedback: the player's answer for the mapping the game has now, and the mappings players say work. */
data class ControllerFeedbackPage(
    val answer: ControllerAnswer? = null,
    val onAnswer: (ControllerAnswer) -> Unit = {},
    val community: List<CommunityMapping> = emptyList(),
)

/** Where [mapping] would send each control a received mapping names, only for the controls the game uses, and only where it differs from what the game does now. */
internal fun changesToApply(mapping: ControllerMapping, received: CommunityMapping): Map<ControlRef, List<ControlRef>> {
    val now = mapping.allocation()
    return received.changesFor(mapping.detected).filter { (source, targets) -> now[source].orEmpty().toSet() != targets.toSet() }
}

/** Whether applying [changes] would move a control the player already chose for to somewhere else: then the player says whether it replaces or comes in addition. */
internal fun conflictsWithChoices(mapping: ControllerMapping, changes: Map<ControlRef, List<ControlRef>>): Boolean =
    mapping.enabled && changes.any { (source, targets) -> mapping.overrides[source.key]?.let { ControllerLayout.parseTargets(it).toSet() != targets.toSet() } == true }

/** The two sections under the mapping: the player's own answer (changeable at any time), and the mappings the community says work, each one applicable in one step. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControllerFeedbackSections(mapping: ControllerMapping, page: ControllerFeedbackPage, onChoose: (Map<ControlRef, List<ControlRef>>) -> Unit) {
    // A received mapping that would move controls the player already chose for waits for them to say whether it replaces or comes in addition.
    var pending by remember { mutableStateOf<Map<ControlRef, List<ControlRef>>?>(null) }
    pending?.let { changes ->
        val now = mapping.allocation()
        BackdropDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.controllers_conflict_title)) },
            text = { Text(stringResource(R.string.controllers_community_conflict)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(onClick = {
                        pending = null
                        onChoose(changes.mapValues { (source, targets) -> (now[source].orEmpty() + targets).distinct() })
                    }) { Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(R.string.controllers_conflict_add)) }
                    GlassButton(onClick = { pending = null; onChoose(changes) }) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(R.string.controllers_conflict_replace))
                    }
                }
            },
            dismissButton = { DangerTextButton(onClick = { pending = null }) { Text(stringResource(R.string.game_settings_cancel)) } },
        )
    }
    val currentText = ControllerFingerprint.of(mapping).mapping

    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.controllers_feedback_section), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.controllers_feedback_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ControllerAnswer.entries.forEach { answer -> AnswerButton(answer, selected = page.answer == answer, enabled = currentText.isNotEmpty()) { page.onAnswer(answer) } }
        }
        if (page.answer == null) Text(stringResource(R.string.controllers_feedback_unanswered), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.controllers_community_section), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.controllers_community_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (page.community.isEmpty()) {
            Text(stringResource(R.string.controllers_community_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        page.community.forEach { received ->
            CommunityRow(mapping, received, isCurrent = received.mapping == currentText) { changes ->
                if (conflictsWithChoices(mapping, changes)) pending = changes else onChoose(changes)
            }
        }
    }
}

/** One answer of the three, as a button with its mark: the one given is outlined in its colour and tinted. */
@Composable
private fun AnswerButton(answer: ControllerAnswer, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = answer.tint()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (selected) ButtonDefaults.outlinedButtonColors(containerColor = tint.copy(alpha = 0.18f), contentColor = Color.White) else ButtonDefaults.outlinedButtonColors(containerColor = app.gameport.core.designsystem.GlassFill, contentColor = Color.White),
        border = if (selected) BorderStroke(2.dp, tint) else BorderStroke(1.dp, app.gameport.core.designsystem.GlassBorder),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        CompatGlyph(answer.glyph(), tint, height = 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(answer.label(), maxLines = 1)
    }
}

@Composable
private fun CommunityRow(mapping: ControllerMapping, received: CommunityMapping, isCurrent: Boolean, onApply: (Map<ControlRef, List<ControlRef>>) -> Unit) {
    var open by rememberSaveable(received.mapping) { mutableStateOf(false) }
    val shown = received.changesFor(mapping.detected)
    val changes = changesToApply(mapping, received)
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A touch on the title shows what the mapping does, control by control (only for the controls the game uses).
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(when {
                    received.automatic -> stringResource(R.string.controllers_community_auto)
                    received.preset != null -> presetName(received.preset!!)
                    else -> stringResource(R.string.controllers_preset_custom)
                }, style = MaterialTheme.typography.titleSmall)
                Text(pluralStringResource(R.plurals.controllers_community_works, received.works, received.works), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (shown.isEmpty()) Text(stringResource(R.string.controllers_community_unused), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                shown.forEach { (source, targets) ->
                    Text(
                        "${targetLabel(source)}  →  " + if (targets.isEmpty()) stringResource(R.string.controllers_nothing) else targets.map { targetLabel(it) }.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (isCurrent) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.controllers_community_current), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            GlassButton(onClick = { onApply(changes) }, enabled = changes.isNotEmpty()) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                Text(stringResource(R.string.controllers_community_apply))
            }
        }
    }
}
