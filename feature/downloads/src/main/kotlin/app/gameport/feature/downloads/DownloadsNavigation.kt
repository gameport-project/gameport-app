package app.gameport.feature.downloads

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DownloadsRoute

fun NavGraphBuilder.downloadsScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit) {
    composable<DownloadsRoute> { DownloadsScreen(onBack = onBack, onGameClick = onGameClick) }
}
