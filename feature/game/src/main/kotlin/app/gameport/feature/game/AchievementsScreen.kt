package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.glass

/** All the achievements of a game for the account: the unlocked ones first, then the ones left, in the language of the player. */
@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val shown = list

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                GoldTrophy(56.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.achievements_title), style = MaterialTheme.typography.headlineMedium)
                    Text(gameName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (shown != null) {
                    Text(stringResource(R.string.achievements_count, shown.unlockedCount, shown.total), style = MaterialTheme.typography.headlineSmall)
                }
            }
            when {
                shown == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
                shown.items.isEmpty() -> Text(stringResource(R.string.achievements_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    AchievementsProgress(shown)
                    // Two columns where the screen is wide, one where it is not; the bottom margin matches the top one.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 380.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shown.ordered(), key = { it.name }) { achievement ->
                            AchievementRow(shown.appId, achievement, iconSize = 56.dp, modifier = Modifier.glass(RoundedCornerShape(14.dp)).padding(12.dp))
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.achievements_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
