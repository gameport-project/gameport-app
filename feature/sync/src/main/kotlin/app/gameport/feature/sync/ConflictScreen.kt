package app.gameport.feature.sync

import app.gameport.core.designsystem.GlassButton
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gameport.core.sync.PendingConflict
import app.gameport.core.sync.SideSummary
import java.util.Date

/** Both versions side by side, with their dates: the player picks which one the game starts with. */
@Composable
internal fun ConflictScreen(
    conflict: PendingConflict,
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onDecideLater: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            modifier = Modifier.padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.conflict_title), style = MaterialTheme.typography.headlineMedium)
            Text(conflict.gameName, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.conflict_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Version(stringResource(R.string.conflict_this_headset), conflict.local, Modifier.weight(1f))
                Version(stringResource(R.string.conflict_steam_cloud), conflict.cloud, Modifier.weight(1f))
            }
            Text(stringResource(R.string.conflict_safe), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onKeepLocal) { Text(stringResource(R.string.conflict_keep_local)) }
                Button(onClick = onUseCloud) { Text(stringResource(R.string.conflict_use_cloud)) }
                GlassButton(onClick = onDecideLater) { Text(stringResource(R.string.conflict_later)) }
            }
        }
    }
}

@Composable
private fun Version(title: String, summary: SideSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(modifier, shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.conflict_files, summary.fileCount, Formatter.formatFileSize(context, summary.totalBytes)))
            Text(
                stringResource(
                    R.string.conflict_last_change,
                    DateFormat.getMediumDateFormat(context).format(Date(summary.newestMillis)) + " " +
                        DateFormat.getTimeFormat(context).format(Date(summary.newestMillis)),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
