package app.gameport.feature.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import app.gameport.core.designsystem.GlassChip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var confirmingReset by remember { mutableStateOf(false) }
    // The Steam Frame (or Meta) control being remapped, while the player picks where it goes.
    var capturing by remember { mutableStateOf<ControlRef?>(null) }

    if (confirmingReset) {
        BackdropDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.controllers_reset_title)) },
            text = { Text(stringResource(R.string.controllers_reset_message)) },
            confirmButton = {
                DangerButton(onClick = { confirmingReset = false; viewModel.onReset() }) { Text(stringResource(R.string.controllers_reset_confirm)) }
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
            confirmButton = { GlassButton(onClick = { viewModel.onTargetsChosen(source, picked); capturing = null }) { Text(stringResource(R.string.controllers_apply)) } },
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
                Column {
                    Text(stringResource(R.string.controllers_title), style = MaterialTheme.typography.headlineMedium)
                    Text(gameName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Only what GamePort does by itself is told here; forcing the Steam Frame's controls adds no message above.
                if (mapping.automatic) {
                    Text(stringResource(R.string.controllers_status_automatic), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(max = 720.dp))
                }
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 720.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Switch(checked = mapping.enabled, onCheckedChange = viewModel::onEnabledChanged, enabled = mapping.translated)
                }

                // Nothing to remap while GamePort puts nothing on the controllers.
                if (mapping.translated) {
                    Text(
                        stringResource(R.string.controllers_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )

                    val shared = mapping.sharedTargets().keys.toList()
                    if (shared.isNotEmpty()) {
                        Text(
                            stringResource(R.string.controllers_shared, shared.map { targetLabel(it) }.joinToString(", ")),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.widthIn(max = 720.dp),
                        )
                    }

                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val wide = maxWidth >= 720.dp
                        val cards: @Composable (Modifier) -> Unit = { modifier ->
                            ControllerCard(Hand.LEFT, mapping, { capturing = it }, modifier)
                            ControllerCard(Hand.RIGHT, mapping, { capturing = it }, modifier)
                        }
                        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { cards(Modifier.weight(1f)) }
                        else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { cards(Modifier.fillMaxWidth()) }
                    }

                    GlassButton(onClick = { confirmingReset = true }, enabled = mapping.overrides.isNotEmpty(), modifier = Modifier.padding(bottom = 24.dp)) {
                        Text(stringResource(R.string.controllers_reset))
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerCard(hand: Hand, mapping: ControllerMapping, onSelect: (ControlRef) -> Unit, modifier: Modifier) {
    val controls = mapping.detected.filter { it.hand == hand && it.group != "thumbrest" }.sortedBy { ORDER.indexOf(it.group).let { i -> if (i < 0) ORDER.size else i } }
    Column(modifier.glass(RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ControllerDrawing(hand)
            Text(stringResource(if (hand == Hand.LEFT) R.string.controllers_left else R.string.controllers_right), style = MaterialTheme.typography.titleLarge)
        }
        if (controls.isEmpty()) Text(stringResource(R.string.controllers_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        controls.forEach { source ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = mapping.enabled) { onSelect(source) }.padding(vertical = 4.dp),
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

/** A simple outline of a controller: the body, the stick and the buttons. */
@Composable
private fun ControllerDrawing(hand: Hand) {
    Canvas(Modifier.size(width = 84.dp, height = 56.dp)) {
        val line = Color.White.copy(alpha = 0.85f)
        drawRoundRect(line, size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height / 2), style = Stroke(3f))
        val stickX = if (hand == Hand.LEFT) size.width * 0.32f else size.width * 0.68f
        drawCircle(line, radius = size.height * 0.16f, center = Offset(stickX, size.height * 0.5f), style = Stroke(3f))
        val buttonsX = if (hand == Hand.LEFT) size.width * 0.72f else size.width * 0.30f
        drawCircle(line, radius = size.height * 0.07f, center = Offset(buttonsX, size.height * 0.34f))
        drawCircle(line, radius = size.height * 0.07f, center = Offset(buttonsX + size.width * 0.08f, size.height * 0.62f))
    }
}

@Composable
private fun controlLabel(group: String): String = when (group) {
    "trigger" -> stringResource(R.string.control_trigger)
    "squeeze" -> stringResource(R.string.control_squeeze)
    "thumbstick" -> stringResource(R.string.control_thumbstick)
    "menu" -> stringResource(R.string.control_menu)
    "view" -> stringResource(R.string.control_view)
    "bumper" -> stringResource(R.string.control_bumper)
    "dpad_up" -> stringResource(R.string.control_dpad_up)
    "dpad_down" -> stringResource(R.string.control_dpad_down)
    "dpad_left" -> stringResource(R.string.control_dpad_left)
    "dpad_right" -> stringResource(R.string.control_dpad_right)
    "a", "b", "x", "y" -> stringResource(R.string.control_button, group.uppercase())
    else -> group.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun targetLabel(target: ControlRef): String =
    stringResource(R.string.controllers_target, stringResource(if (target.hand == Hand.LEFT) R.string.controllers_left_short else R.string.controllers_right_short), controlLabel(target.group))

private val ORDER = listOf("thumbstick", "trigger", "squeeze", "bumper", "a", "b", "x", "y", "menu", "view", "dpad_up", "dpad_down", "dpad_left", "dpad_right")
