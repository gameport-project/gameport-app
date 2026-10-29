package app.gameport.feature.store

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The list of the Steam games that have an Android version. */
@Serializable
data object StoreRoute

/** The page of one game of that list: its details, and the way to its page on Steam. */
@Serializable
data class StoreGameRoute(val appId: Int)

/** [onGameClick] opens the page of a game of the library; [onStoreGame] the page of any other. */
fun NavGraphBuilder.storeScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit, onStoreGame: (Int) -> Unit) {
    composable<StoreRoute> { StoreScreen(onBack = onBack, onGameClick = onGameClick, onStoreGame = onStoreGame) }
}

fun NavGraphBuilder.storeGameScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit, onOpenSteam: (url: String, title: String) -> Unit) {
    composable<StoreGameRoute> { StoreGameScreen(onBack = onBack, onGameClick = onGameClick, onOpenSteam = onOpenSteam) }
}
