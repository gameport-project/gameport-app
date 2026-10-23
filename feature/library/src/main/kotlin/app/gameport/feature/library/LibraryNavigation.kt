package app.gameport.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen(onGameClick: (Int) -> Unit, onOpenDownloads: () -> Unit, onOpenSettings: () -> Unit, onOpenSteamSettings: () -> Unit, onOpenUpdates: () -> Unit, onOpenGameSettings: (Int) -> Unit, onOpenCompat: () -> Unit, onOpenStore: () -> Unit, onOpenStoreGame: (Int) -> Unit) {
    composable<LibraryRoute> { LibraryScreen(onGameClick = onGameClick, onOpenDownloads = onOpenDownloads, onOpenSettings = onOpenSettings, onOpenSteamSettings = onOpenSteamSettings, onOpenUpdates = onOpenUpdates, onOpenGameSettings = onOpenGameSettings, onOpenCompat = onOpenCompat, onOpenStore = onOpenStore, onOpenStoreGame = onOpenStoreGame) }
}
