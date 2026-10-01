package app.gameport

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.gameport.core.model.AuthState
import app.gameport.feature.auth.AuthRoute
import app.gameport.feature.auth.authScreen
import app.gameport.feature.downloads.DownloadsRoute
import app.gameport.feature.downloads.downloadsScreen
import app.gameport.feature.game.gameScreen
import app.gameport.feature.game.gameControllersScreen
import app.gameport.feature.game.gameSavesScreen
import app.gameport.feature.game.gameSettingsScreen
import app.gameport.feature.game.navigateToGameControllers
import app.gameport.feature.game.navigateToGameSaves
import app.gameport.feature.game.navigateToGameSettings
import app.gameport.feature.game.navigateToGame
import app.gameport.feature.library.LibraryRoute
import app.gameport.feature.settings.SettingsRoute
import app.gameport.feature.settings.settingsScreen
import app.gameport.feature.library.libraryScreen

@Composable
fun GamePortApp(viewModel: AppViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(keepScreenOn, window) {
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val navController = rememberNavController()
    val signedIn = authState is AuthState.SignedIn

    LaunchedEffect(signedIn) {
        val alreadyThere = if (signedIn) {
            navController.currentDestination?.hasRoute<LibraryRoute>()
        } else {
            navController.currentDestination?.hasRoute<AuthRoute>()
        }
        if (alreadyThere == true) return@LaunchedEffect

        val target: Any = if (signedIn) LibraryRoute else AuthRoute
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = AuthRoute) {
        authScreen()
        libraryScreen(
            onGameClick = navController::navigateToGame,
            onOpenDownloads = { navController.navigate(DownloadsRoute) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )
        settingsScreen(onBack = navController::popBackStack)
        downloadsScreen(onBack = navController::popBackStack, onGameClick = navController::navigateToGame)
        gameScreen(onBack = navController::popBackStack, onOpenSettings = navController::navigateToGameSettings, onOpenSaves = navController::navigateToGameSaves, onOpenControllers = navController::navigateToGameControllers)
        gameControllersScreen(onBack = navController::popBackStack)
        gameSavesScreen(onBack = navController::popBackStack)
        gameSettingsScreen(onBack = navController::popBackStack)
    }
}
