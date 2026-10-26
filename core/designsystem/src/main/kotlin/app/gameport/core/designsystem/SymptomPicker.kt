package app.gameport.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gameport.core.model.Symptom

/** The words of a symptom, the way the player reads them in a report. */
@Composable
fun symptomLabel(symptom: Symptom): String = stringResource(
    when (symptom) {
        Symptom.NO_IMAGE -> R.string.symptom_no_image
        Symptom.FLAT_WINDOW -> R.string.symptom_flat_window
        Symptom.CLOSES -> R.string.symptom_closes
        Symptom.STUCK_LOADING -> R.string.symptom_stuck_loading
        Symptom.NO_CONTROLS -> R.string.symptom_no_controls
        Symptom.NO_SOUND -> R.string.symptom_no_sound
        Symptom.NO_ONLINE -> R.string.symptom_no_online
        Symptom.SLOW -> R.string.symptom_slow
        Symptom.NO_PASSTHROUGH -> R.string.symptom_no_passthrough
        Symptom.VISUAL -> R.string.symptom_visual
        Symptom.OTHER -> R.string.symptom_other
    },
)

/**
 * What went wrong, to tick: one rounded glass field that opens a list where several can be ticked, and shows what was chosen. The list is on the player's
 * background, like the other menus. They are written in the report as fixed codes, so they can be counted and shown as they are.
 */
@Composable
fun SymptomPicker(selected: Set<Symptom>, onToggle: (Symptom) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val labels = Symptom.entries.map { it to symptomLabel(it) }
    val summary = labels.filter { it.first in selected }.joinToString(", ") { it.second }
    val shape = RoundedCornerShape(50)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.symptom_picker_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Row(
                Modifier.fillMaxWidth().height(48.dp).glass(shape).clickable { open = true }.padding(start = 18.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    summary.ifEmpty { stringResource(R.string.symptom_picker_hint) },
                    color = if (summary.isEmpty()) Color.White.copy(alpha = 0.6f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (open) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            }
            // The list stays open while several are ticked.
            BackdropMenu(expanded = open, onDismissRequest = { open = false }) {
                labels.forEach { (symptom, label) ->
                    val on = symptom in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(symptom) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = if (on) KindBlue else Color.Transparent, modifier = Modifier.size(18.dp))
                        Text(label, color = if (on) KindBlue else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
