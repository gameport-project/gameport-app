package app.gameport.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The header of a page, as the page of the trophies has it: the back arrow, a mark, the title and a line under it. */
@Composable
fun PageHeader(onBack: () -> Unit, title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null, tour: app.gameport.core.model.TourKind? = null, mark: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton(onClick = onBack)
        mark()
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false))
                trailing?.invoke()
            }
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The help button stands at the far end of the header.
        if (tour != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            TourHelpButton(tour)
        }
    }
}
