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

@Serializable
data class GameAchievementsRoute(val appId: Int)

@Serializable
data class GameCompatRoute(val appId: Int)

@Serializable
data class GameWorkshopRoute(val appId: Int)

fun NavController.navigateToGame(appId: Int) = navigate(GameRoute(appId))

fun NavController.navigateToGameSettings(appId: Int) = navigate(GameSettingsRoute(appId))

fun NavController.navigateToGameSaves(appId: Int) = navigate(GameSavesRoute(appId))

fun NavController.navigateToGameControllers(appId: Int) = navigate(GameControllersRoute(appId))

fun NavController.navigateToGameAchievements(appId: Int) = navigate(GameAchievementsRoute(appId))

fun NavController.navigateToGameCompat(appId: Int) = navigate(GameCompatRoute(appId))

fun NavController.navigateToGameWorkshop(appId: Int) = navigate(GameWorkshopRoute(appId))

/** A mock-up of a game's page with fixed data, to try a layout (see [GameMockupScreen]). */
@Serializable
data object GameMockupRoute

fun NavGraphBuilder.gameMockupScreen(onBack: () -> Unit) {
    composable<GameMockupRoute> { GameMockupScreen(onBack = onBack) }
}

fun NavGraphBuilder.gameScreen(onBack: () -> Unit, onOpenSettings: (Int) -> Unit, onOpenSaves: (Int) -> Unit, onOpenControllers: (Int) -> Unit, onOpenCompat: (Int) -> Unit, onOpenAchievements: (Int) -> Unit, onOpenSteamSettings: () -> Unit, onOpenWorkshop: (Int) -> Unit, onOpenStore: (Int) -> Unit) {
    composable<GameRoute> { entry ->
        val appId = entry.toRoute<GameRoute>().appId
        GameScreen(onBack = onBack, onOpenSettings = { onOpenSettings(appId) }, onOpenSaves = { onOpenSaves(appId) }, onOpenControllers = { onOpenControllers(appId) }, onOpenCompat = { onOpenCompat(appId) }, onOpenAchievements = { onOpenAchievements(appId) }, onOpenSteamSettings = onOpenSteamSettings, onOpenWorkshop = { onOpenWorkshop(appId) }, onOpenStore = { onOpenStore(appId) })
    }
}

fun NavGraphBuilder.gameWorkshopScreen(onBack: () -> Unit) {
    composable<GameWorkshopRoute> { WorkshopScreen(onBack = onBack) }
}

fun NavController.navigateToSteamPage(url: String, title: String) = navigate(SteamPageRoute(url, title))

fun NavGraphBuilder.steamPageScreen(onBack: () -> Unit) {
    composable<SteamPageRoute> { SteamPageScreen(onBack = onBack) }
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

fun NavGraphBuilder.gameCompatScreen(onBack: () -> Unit) {
    composable<GameCompatRoute> { GameCompatScreen(onBack = onBack) }
}

fun NavGraphBuilder.gameAchievementsScreen(onBack: () -> Unit) {
    composable<GameAchievementsRoute> { AchievementsScreen(onBack = onBack) }
}
