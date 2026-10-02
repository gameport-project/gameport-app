package app.gameport.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen(onGameClick: (Int) -> Unit, onOpenDownloads: () -> Unit, onOpenSettings: () -> Unit, onOpenSteamSettings: () -> Unit) {
    composable<LibraryRoute> { LibraryScreen(onGameClick = onGameClick, onOpenDownloads = onOpenDownloads, onOpenSettings = onOpenSettings, onOpenSteamSettings = onOpenSteamSettings) }
}
