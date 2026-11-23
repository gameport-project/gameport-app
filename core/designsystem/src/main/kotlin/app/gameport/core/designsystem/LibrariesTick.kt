package app.gameport.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.gameport.core.model.LibraryChoice

/**
 * The box that adds the game's libraries to a report: what would go (how many files, about how much), and that a Discord name is then required. Nothing is shown when the game holds
 * no library to send, or while it is not known yet (null).
 */
@Composable
fun LibrariesTick(plan: LibraryChoice.Pick?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    if (plan == null || plan.included.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.libraries_tick_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.libraries_tick_summary, plan.included.size, sizeText(plan.bytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.libraries_tick_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun sizeText(bytes: Long): String =
    if (bytes < 1_000_000) stringResource(R.string.libraries_tick_size_kb, (bytes / 1000).toInt().coerceAtLeast(1))
    else stringResource(R.string.libraries_tick_size_mb, bytes / 1_000_000f)
