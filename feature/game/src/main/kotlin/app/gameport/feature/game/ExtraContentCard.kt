package app.gameport.feature.game

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.model.ExtraContent
import app.gameport.core.model.ExtraContentItem

/** The extra content of the game and what can be done with it, passed down to its page. */
internal data class ExtraContentActions(
    val state: ExtraContent = ExtraContent(),
    val onInstall: (Int) -> Unit = {},
    val onRemove: (Int) -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/**
 * The extra content (DLC) of an installed game that the account has: each with its size, whether it is on the device, and what can be done with it (install, take off).
 * One thing is done at a time: while it runs, its row shows the progress and every button waits.
 */
@Composable
internal fun ExtraContentCard(
    extra: ExtraContent,
    onInstall: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val work = extra.work
    val busy = work != null && work.error == null
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.extra_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.extra_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        extra.items.forEach { item -> ExtraContentRow(item, extra, busy, context, onInstall, onRemove) }
        work?.error?.let { error ->
            Text(stringResource(R.string.extra_failed, error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            GlassButton(onClick = onDismissError, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.issue_dismiss)) }
        }
    }
}

@Composable
private fun ExtraContentRow(
    item: ExtraContentItem,
    extra: ExtraContent,
    busy: Boolean,
    context: android.content.Context,
    onInstall: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val working = extra.work?.takeIf { it.dlcAppId == item.appId && it.error == null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        working?.removing == true -> stringResource(R.string.extra_removing)
                        working != null -> stringResource(R.string.extra_downloading, (working.progress * 100).toInt())
                        item.installed -> stringResource(R.string.extra_installed)
                        else -> stringResource(R.string.extra_not_installed, Formatter.formatFileSize(context, item.downloadBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                item.installed && item.removable -> GlassButton(onClick = { onRemove(item.appId) }, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.extra_remove))
                }
                item.installed -> Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.extra_installed), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                else -> GlassButton(onClick = { onInstall(item.appId) }, enabled = !busy) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.extra_install))
                }
            }
        }
        if (working != null && !working.removing) LinearProgressIndicator(progress = { working.progress }, modifier = Modifier.fillMaxWidth())
        if (item.installed && !item.removable) {
            Text(stringResource(R.string.extra_with_game), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
