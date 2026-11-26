package app.gameport.feature.game

import app.gameport.core.designsystem.TourIds
import app.gameport.core.designsystem.tourTarget
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import app.gameport.core.designsystem.GlassChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.DangerButton
import app.gameport.core.designsystem.DangerTextButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerLayout
import app.gameport.core.model.ControllerPreset
import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.Hand

/**
 * Lets the player choose, for one game, where each control of the Steam Frame's controllers goes on
 * the controllers of this device. Off by default: GamePort's own mapping applies until it is switched on.
 */
@Composable
fun ControllersScreen(onBack: () -> Unit, viewModel: ControllersViewModel = hiltViewModel()) {
    val mapping by viewModel.mapping.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val answer by viewModel.myAnswer.collectAsStateWithLifecycle()
    val community by viewModel.community.collectAsStateWithLifecycle()
    ControllersContent(
        mapping, gameName, onBack, viewModel::onEnabledChanged, viewModel::onChoose, viewModel::onReset, viewModel.deviceFamily,
        canUndo = history.canUndo, canRedo = history.canRedo, onUndo = viewModel::onUndo, onRedo = viewModel::onRedo,
        onPreset = viewModel::onPreset,
        feedback = ControllerFeedbackPage(answer, viewModel::onAnswer, community),
    )
}

/** The page of the controllers, from what it shows: the screen feeds it from the game, the tour of the page feeds it made-up values. */
@Composable
internal fun ControllersContent(
    mapping: ControllerMapping,
    gameName: String,
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onChoose: (Map<ControlRef, List<ControlRef>>) -> Unit,
    onReset: () -> Unit,
    deviceFamily: String? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    /** A ready-made preset chosen from the menu, or automatic when null. */
    onPreset: (ControllerPreset?) -> Unit = {},
    /** The player's answer about the controls and the mappings players say work; null where there is nothing to say (the tour of the page). */
    feedback: ControllerFeedbackPage? = null,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    // The Steam Frame (or Meta) control being remapped, while the player picks where it goes.
    var capturing by remember { mutableStateOf<ControlRef?>(null) }
    // The control selected on the drawing or in the list: its destination stands out on the device's controller.
    var focused by remember { mutableStateOf<ControlRef?>(null) }
    // A touch on the drawing only selects a control (its destination lights up); the window to choose it opens from the panel under the drawing.
    val select: (ControlRef?) -> Unit = { source -> focused = source }
    // A touch on a row of the list selects it and, when the mapping can be customised, opens the window at once.
    val selectFromList: (ControlRef) -> Unit = { source -> focused = source; if (mapping.enabled) capturing = source }
    var showList by remember { mutableStateOf(false) }
    // Which controller is on the left of the drawing: the one of this device (where the player's own buttons are) unless the player swaps them.
    var deviceFirst by rememberSaveable { mutableStateOf(true) }
    // A link that would sit next to existing ones waits for the player to say whether it replaces them or comes in addition.
    var conflict by remember { mutableStateOf<LinkConflict?>(null) }
    val requestLink: (ControlRef, ControlRef) -> Unit = { source, target ->
        val allocation = mapping.allocation()
        val goesTo = allocation[source].orEmpty()
        val triggers = allocation.filter { (other, targets) -> other != source && target in targets }.keys.toList()
        when {
            // The link exists: touching it again takes it away (and undo brings it back).
            target in goesTo -> onChoose(mapOf(source to goesTo - target))
            goesTo.isEmpty() && triggers.isEmpty() -> onChoose(mapOf(source to listOf(target)))
            else -> conflict = LinkConflict(source, target, goesTo, triggers)
        }
    }

    conflict?.let { c ->
        val allocation = mapping.allocation()
        BackdropDialog(
            onDismissRequest = { conflict = null },
            title = { Text(stringResource(R.string.controllers_conflict_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.goesTo.isNotEmpty()) Text(stringResource(R.string.controllers_conflict_goes, targetLabel(c.source), c.goesTo.map { targetLabel(it) }.joinToString(", ")))
                    if (c.triggers.isNotEmpty()) Text(stringResource(R.string.controllers_conflict_triggers, targetLabel(c.target), c.triggers.map { targetLabel(it) }.joinToString(", ")))
                    Text(stringResource(R.string.controllers_conflict_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(onClick = {
                        conflict = null
                        onChoose(mapOf(c.source to c.goesTo + c.target))
                    }) { Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(R.string.controllers_conflict_add)) }
                    GlassButton(onClick = {
                        conflict = null
                        // Replacing: the control goes only here, and the others no longer trigger this button.
                        val changes = LinkedHashMap<ControlRef, List<ControlRef>>()
                        changes[c.source] = listOf(c.target)
                        for (other in c.triggers) changes[other] = allocation[other].orEmpty() - c.target
                        onChoose(changes)
                    }) { Icon(Icons.Filled.Check, null, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(R.string.controllers_conflict_replace)) }
                }
            },
            dismissButton = { DangerTextButton(onClick = { conflict = null }) { Text(stringResource(R.string.game_settings_cancel)) } },
        )
    }

    if (confirmingReset) {
        BackdropDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.controllers_reset_title)) },
            text = { Text(stringResource(R.string.controllers_reset_message)) },
            confirmButton = {
                DangerButton(onClick = { confirmingReset = false; onReset() }) { Text(stringResource(R.string.controllers_reset_confirm)) }
            },
            dismissButton = { DangerTextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.game_settings_cancel)) } },
        )
    }

    capturing?.let { source ->
        // One control of the Steam Frame may go to several of this device: the ones ticked here. None ticked: nowhere.
        var picked by remember(source) { mutableStateOf(mapping.targetsFor(source)) }
        BackdropDialog(
            onDismissRequest = { capturing = null },
            title = { Text(stringResource(R.string.controllers_choose_title, controlLabel(source.group))) },
            text = {
                // No scrolling of its own: the dialog already scrolls its text, and a scrolling column inside it is measured without a limit, which crashes.
                Column {
                    Text(stringResource(R.string.controllers_choose_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    ControllerLayout.targets.forEach { target ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picked = if (target in picked) picked - target else picked + target },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = target in picked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(targetLabel(target))
                        }
                    }
                }
            },
            confirmButton = { GlassButton(onClick = { onChoose(mapOf(source to picked)); capturing = null }) { Text(stringResource(R.string.controllers_apply)) } },
            dismissButton = { DangerTextButton(onClick = { capturing = null }) { Text(stringResource(R.string.game_settings_cancel)) } },
        )
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.controllers_title), style = MaterialTheme.typography.headlineMedium)
                    Text(gameName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                app.gameport.core.designsystem.TourHelpButton(app.gameport.core.model.TourKind.GAME_CONTROLLERS)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Only what GamePort does by itself is told here; forcing the Steam Frame's controls adds no message above.
                if (mapping.automatic) {
                    Text(stringResource(R.string.controllers_status_automatic), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(max = 720.dp))
                }
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 720.dp).tourTarget(TourIds.CONTROLLERS_SWITCH).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.controllers_customise), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (!mapping.translated) R.string.controllers_customise_unavailable else if (mapping.enabled) R.string.controllers_customise_on else R.string.controllers_customise_off),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = mapping.enabled, onCheckedChange = onEnabledChanged, enabled = mapping.translated)
                }

                // Nothing to remap while GamePort puts nothing on the controllers.
                if (mapping.translated) {
                    Text(
                        stringResource(R.string.controllers_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )

                    Column(Modifier.fillMaxWidth().tourTarget(TourIds.CONTROLLERS_CARDS), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Ready-made mappings for the cases that come back from game to game: one touch instead of adding and removing links by hand.
                        var presetMenu by remember { mutableStateOf(false) }
                        // The automatic mapping is in effect while nothing is customised, even when it happens to match a preset: the choice shows "Automatic", not that preset.
                        val onAutomatic = !mapping.enabled || mapping.overrides.isEmpty()
                        // The chosen preset is stored, not guessed: any hand-made retouch clears it, so a touched-up mapping shows "Custom".
                        val currentPreset = if (onAutomatic) null else mapping.chosenPreset
                        // Only the presets that change something for this game are offered: one that touches controls the game does not use would do nothing.
                        val offered = remember(mapping.detected) { ControllerPreset.entries.filter { it.changesFor(mapping.detected).isNotEmpty() } }
                        Box {
                            GlassButton(onClick = { presetMenu = true }) {
                                Text(
                                    stringResource(
                                        R.string.controllers_preset_label,
                                        when {
                                            onAutomatic -> stringResource(R.string.controllers_preset_auto)
                                            currentPreset != null -> presetName(currentPreset)
                                            else -> stringResource(R.string.controllers_preset_custom)
                                        },
                                    ),
                                )
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.controllers_preset_auto))
                                            Text(stringResource(R.string.controllers_preset_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    trailingIcon = { if (onAutomatic) Icon(Icons.Filled.Check, null) },
                                    onClick = { presetMenu = false; if (mapping.enabled) confirmingReset = true },
                                )
                                offered.forEach { preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(presetName(preset))
                                                Text(presetHint(preset), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        trailingIcon = { if (currentPreset == preset) Icon(Icons.Filled.Check, null) },
                                        // The preset replaces the links of its family and freezes the rest; the choice is remembered. One step, so one undo takes it back.
                                        onClick = { presetMenu = false; onPreset(preset) },
                                    )
                                }
                            }
                        }
                        // Undo and redo for every choice, and the side each controller is drawn on.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton(onClick = onUndo, enabled = canUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.controllers_undo))
                            }
                            GlassButton(onClick = onRedo, enabled = canRedo) {
                                Icon(Icons.AutoMirrored.Filled.Redo, null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.controllers_redo))
                            }
                            Spacer(Modifier.weight(1f))
                            GlassButton(onClick = { deviceFirst = !deviceFirst }) {
                                Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.controllers_swap))
                            }
                        }
                        ControllerStage(mapping, deviceFamily, focused, select, onToggleTarget = requestLink, deviceFirst = deviceFirst)
                        // The list stays available, folded: the same choices, for a screen reader or a finger that misses a small control.
                        GlassButton(onClick = { showList = !showList }) {
                            Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(if (showList) R.string.controllers_list_hide else R.string.controllers_list_show))
                        }
                        if (showList) BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val wide = maxWidth >= 720.dp
                            val cards: @Composable (Modifier) -> Unit = { modifier ->
                                ControllerCard(Hand.LEFT, mapping, selectFromList, modifier)
                                ControllerCard(Hand.RIGHT, mapping, selectFromList, modifier)
                            }
                            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { cards(Modifier.weight(1f)) }
                            else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { cards(Modifier.fillMaxWidth()) }
                        }
                    }

                    GlassButton(onClick = { confirmingReset = true }, enabled = mapping.overrides.isNotEmpty(), modifier = Modifier.padding(bottom = 24.dp).tourTarget(TourIds.CONTROLLERS_RESET)) {
                        Text(stringResource(R.string.controllers_reset))
                    }
                    if (feedback != null) ControllerFeedbackSections(mapping, feedback, onChoose)
                }
            }
        }
    }
}

/** A link the player asked for that would sit next to existing ones: [goesTo] are where its control already goes, [triggers] the controls its button already triggers. */
private data class LinkConflict(val source: ControlRef, val target: ControlRef, val goesTo: List<ControlRef>, val triggers: List<ControlRef>)

@Composable
private fun ControllerCard(hand: Hand, mapping: ControllerMapping, onSelect: (ControlRef) -> Unit, modifier: Modifier) {
    val controls = mapping.detected.filter { it.hand == hand && it.group != "thumbrest" }.sortedBy { ORDER.indexOf(it.group).let { i -> if (i < 0) ORDER.size else i } }
    Column(modifier.glass(RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(if (hand == Hand.LEFT) R.string.controllers_left else R.string.controllers_right), style = MaterialTheme.typography.titleLarge)
        if (controls.isEmpty()) Text(stringResource(R.string.controllers_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        controls.forEach { source ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(source) }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(controlLabel(source.group), modifier = Modifier.weight(1f))
                val targets = mapping.targetsFor(source)
                val color = if (mapping.enabled) Color.White else Color.White.copy(alpha = 0.5f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (targets.isEmpty()) GlassChip(label = stringResource(R.string.controllers_nothing), contentColor = color)
                    targets.forEach { GlassChip(label = targetLabel(it), contentColor = color) }
                    // Adding a button: the same window, where more than one can be ticked.
                    if (mapping.enabled) Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.controllers_add), tint = color, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun presetName(preset: ControllerPreset): String = stringResource(
    when (preset) {
        ControllerPreset.FACE_BUTTONS -> R.string.controllers_preset_faces
        ControllerPreset.DPAD_VERTICAL -> R.string.controllers_preset_dpad_vertical
        ControllerPreset.DPAD_HORIZONTAL -> R.string.controllers_preset_dpad_horizontal
    },
)

@Composable
private fun presetHint(preset: ControllerPreset): String = stringResource(
    when (preset) {
        ControllerPreset.FACE_BUTTONS -> R.string.controllers_preset_faces_hint
        ControllerPreset.DPAD_VERTICAL -> R.string.controllers_preset_dpad_vertical_hint
        ControllerPreset.DPAD_HORIZONTAL -> R.string.controllers_preset_dpad_horizontal_hint
    },
)

@Composable
internal fun controlLabel(group: String): String = when (group) {
    "trigger" -> stringResource(R.string.control_trigger)
    "squeeze" -> stringResource(R.string.control_squeeze)
    "thumbstick" -> stringResource(R.string.control_thumbstick)
    "menu" -> stringResource(R.string.control_menu)
    "view" -> stringResource(R.string.control_view)
    "bumper", "shoulder" -> stringResource(R.string.control_bumper)
    "dpad_up" -> stringResource(R.string.control_dpad_up)
    "dpad_down" -> stringResource(R.string.control_dpad_down)
    "dpad_left" -> stringResource(R.string.control_dpad_left)
    "dpad_right" -> stringResource(R.string.control_dpad_right)
    "a", "b", "x", "y" -> stringResource(R.string.control_button, group.uppercase())
    else -> group.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
internal fun targetLabel(target: ControlRef): String =
    stringResource(R.string.controllers_target, stringResource(if (target.hand == Hand.LEFT) R.string.controllers_left_short else R.string.controllers_right_short), controlLabel(target.group))

private val ORDER = listOf("thumbstick", "trigger", "squeeze", "bumper", "shoulder", "a", "b", "x", "y", "menu", "view", "dpad_up", "dpad_down", "dpad_left", "dpad_right")
