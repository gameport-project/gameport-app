package app.gameport.feature.game

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class GameRoute(val appId: Int)

@Serializable
data class GameSettingsRoute(val appId: Int)

@Serializable
data class GameSavesRoute(val appId: Int)

@Serializable
data class GameControllersRoute(val appId: Int)

fun NavController.navigateToGame(appId: Int) = navigate(GameRoute(appId))

fun NavController.navigateToGameSettings(appId: Int) = navigate(GameSettingsRoute(appId))

fun NavController.navigateToGameSaves(appId: Int) = navigate(GameSavesRoute(appId))

fun NavController.navigateToGameControllers(appId: Int) = navigate(GameControllersRoute(appId))

fun NavGraphBuilder.gameScreen(onBack: () -> Unit, onOpenSettings: (Int) -> Unit, onOpenSaves: (Int) -> Unit, onOpenControllers: (Int) -> Unit, onOpenSteamSettings: () -> Unit) {
    composable<GameRoute> { entry ->
        val appId = entry.toRoute<GameRoute>().appId
        GameScreen(onBack = onBack, onOpenSettings = { onOpenSettings(appId) }, onOpenSaves = { onOpenSaves(appId) }, onOpenControllers = { onOpenControllers(appId) }, onOpenSteamSettings = onOpenSteamSettings)
    }
}

fun NavGraphBuilder.gameSettingsScreen(onBack: () -> Unit) {
    composable<GameSettingsRoute> { GameSettingsScreen(onBack = onBack) }
}

fun NavGraphBuilder.gameSavesScreen(onBack: () -> Unit) {
    composable<GameSavesRoute> { SavesScreen(onBack = onBack) }
}

fun NavGraphBuilder.gameControllersScreen(onBack: () -> Unit) {
    composable<GameControllersRoute> { ControllersScreen(onBack = onBack) }
}
