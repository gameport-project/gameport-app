package app.gameport.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.model.PatchAllInfo

/** The games patched by an older GamePort, and the button that patches them all again. */
@Composable
internal fun PatchAllRow(info: PatchAllInfo, onPatchAll: () -> Unit) {
    val progress = info.progress
    Row(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_patch_all), style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    info.running && progress != null -> stringResource(R.string.settings_patch_all_running, (progress.done + 1).coerceAtMost(progress.total), progress.total)
                    info.behind == 0 -> stringResource(R.string.settings_patch_all_none)
                    else -> stringResource(R.string.settings_patch_all_description, info.behind)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlassButton(onClick = onPatchAll, enabled = info.behind > 0 || info.running) {
            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (info.running) R.string.settings_patch_all_show else R.string.settings_patch_all_button))
        }
    }
}
