package app.gameport

import androidx.navigation.NavController
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.compose.currentBackStackEntryAsState
import app.gameport.core.designsystem.LocalTourTargets
import app.gameport.core.designsystem.TourOverlay
import app.gameport.core.designsystem.TourTransition
import app.gameport.core.designsystem.TourTargets
import app.gameport.core.model.TourKind
import app.gameport.feature.game.gameDemoScreens
import app.gameport.feature.game.navigateToGame
import app.gameport.feature.game.isTourDemo
import app.gameport.feature.game.navigateToTourDemo
import app.gameport.feature.downloads.downloadsDemoScreen
import app.gameport.feature.downloads.isDownloadsDemo
import app.gameport.feature.downloads.navigateToDownloadsDemo
import app.gameport.feature.store.isStoreGameDemo
import app.gameport.feature.store.navigateToStoreGameDemo
import app.gameport.feature.store.storeGameDemoScreen
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.gameport.core.model.AuthState
import app.gameport.feature.auth.AuthRoute
import app.gameport.feature.auth.authScreen
import app.gameport.feature.compat.CompatRoute
import app.gameport.feature.compat.compatScreen
import app.gameport.feature.store.StoreGameRoute
import app.gameport.feature.store.StoreRoute
import app.gameport.feature.store.storeGameScreen
import app.gameport.feature.store.storeScreen
import app.gameport.feature.downloads.DownloadsRoute
import app.gameport.feature.downloads.downloadsScreen
import app.gameport.feature.game.gameScreen
import app.gameport.feature.game.gameCompatScreen
import app.gameport.feature.game.gameControllersScreen
import app.gameport.feature.game.navigateToGameCompat
import app.gameport.feature.game.gameAchievementsScreen
import app.gameport.feature.game.navigateToGameAchievements
import app.gameport.feature.game.gameSavesScreen
import app.gameport.feature.game.gameSettingsScreen
import app.gameport.feature.game.navigateToGameControllers
import app.gameport.feature.game.navigateToGameSaves
import app.gameport.feature.game.navigateToGameSettings
import app.gameport.feature.library.LibraryRoute
import app.gameport.feature.settings.SettingsRoute
import app.gameport.feature.settings.settingsScreen
import app.gameport.feature.library.libraryScreen

@Composable
fun GamePortApp(viewModel: AppViewModel = hiltViewModel()) {
    // The height of the app's window is known to the dialogs, which keep to about 70 % of it.
    app.gameport.core.designsystem.ProvideAppWindowHeight { GamePortContent(viewModel) }
}

@Composable
private fun GamePortContent(viewModel: AppViewModel) {
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

    val targets = remember { TourTargets() }
    CompositionLocalProvider(LocalTourTargets provides targets, app.gameport.core.designsystem.LocalTourStarter provides { kind -> viewModel.tour.start(kind) }) {
    NavHost(navController = navController, startDestination = AuthRoute) {
        authScreen()
        libraryScreen(
            onGameClick = navController::navigateToGame,
            onOpenDownloads = { navController.navigate(DownloadsRoute) },
            onOpenSettings = { navController.navigate(SettingsRoute()) },
            onOpenSteamSettings = { navController.navigate(SettingsRoute(account = true)) },
            onOpenUpdates = { navController.navigate(SettingsRoute(updates = true)) },
            onOpenGameSettings = navController::navigateToGameSettings,
            onOpenCompat = { navController.navigate(CompatRoute) },
            onOpenStore = { navController.navigate(StoreRoute) },
            onOpenStoreGame = { navController.navigate(StoreGameRoute(it)) },
        )
        compatScreen(onBack = navController::goBack, onGameClick = navController::navigateToGame, onUnownedClick = { navController.navigate(StoreGameRoute(it)) })
        storeScreen(onBack = navController::goBack, onGameClick = navController::navigateToGame, onStoreGame = { navController.navigate(StoreGameRoute(it)) })
        storeGameScreen(onBack = navController::goBack, onGameClick = navController::navigateToGame)
        settingsScreen(onBack = navController::goBack, onOpenGame = navController::navigateToGame)
        downloadsScreen(onBack = navController::goBack, onGameClick = navController::navigateToGame)
        gameScreen(onBack = navController::goBack, onOpenSettings = navController::navigateToGameSettings, onOpenSaves = navController::navigateToGameSaves, onOpenControllers = navController::navigateToGameControllers, onOpenCompat = navController::navigateToGameCompat, onOpenAchievements = navController::navigateToGameAchievements, onOpenSteamSettings = { navController.navigate(SettingsRoute(account = true)) })
        gameControllersScreen(onBack = navController::goBack)
        gameCompatScreen(onBack = navController::goBack)
        gameAchievementsScreen(onBack = navController::goBack)
        gameSavesScreen(onBack = navController::goBack)
        gameSettingsScreen(onBack = navController::goBack)
        // Pages of made-up values, for the guided tours.
        gameDemoScreens(onBack = navController::goBack)
        downloadsDemoScreen(onBack = navController::goBack)
        storeGameDemoScreen(onBack = navController::goBack)
    }
    // What a new version changes, and the games to patch again when it needs it.
    if (signedIn) app.gameport.whatsnew.WhatsNewHost()
    if (signedIn) TourHost(navController, viewModel)
    }
}

/**
 * Goes back one page, but never past the first one: a second tap on a back arrow, arriving while the page is
 * already closing, would otherwise pop the library too and leave an empty screen.
 */
private fun NavController.goBack() {
    if (previousBackStackEntry != null) popBackStack()
}

/**
 * The guided tours. The complete one opens by itself the first time and goes through every page; each page's own can be started from Settings > Help or
 * from the help button of the page. The page of a tour is opened first when it is not on screen, and the pages of a game (which a first launch has none
 * of) are pages of made-up values, so the tours show every button and notice. When a tour on such a page ends, the page is left.
 */
@Composable
private fun TourHost(navController: NavController, viewModel: AppViewModel) {
    val tour = viewModel.tour
    val active by tour.active.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onHome = destination?.hasRoute<LibraryRoute>() == true

    // The first time, the home shows itself a moment, then the complete tour starts.
    LaunchedEffect(onHome) {
        if (onHome && tour.firstLaunchDue()) {
            delay(1_200)
            if (tour.firstLaunchDue()) tour.startComplete()
        }
    }
    val kind = active
    val onPage = kind != null && destination?.let { isPageOf(kind, it) } == true
    // A tour brings its page first. In the complete tour each page replaces the last, so the pages do not pile up.
    LaunchedEffect(kind, onPage) {
        if (kind == null || onPage) return@LaunchedEffect
        if (tour.chaining && kind != TourKind.HOME) navController.popBackStack<LibraryRoute>(inclusive = false)
        when (kind) {
            TourKind.HOME -> navController.popBackStack<LibraryRoute>(inclusive = false)
            TourKind.STORE -> navController.navigate(StoreRoute)
            TourKind.STORE_GAME -> navController.navigateToStoreGameDemo()
            TourKind.COMPAT_TABLE -> navController.navigate(CompatRoute)
            TourKind.DOWNLOADS -> navController.navigateToDownloadsDemo()
            TourKind.SETTINGS -> navController.navigate(SettingsRoute())
            TourKind.GAME, TourKind.GAME_COMPAT, TourKind.GAME_CONTROLLERS, TourKind.GAME_SAVES -> navController.navigateToTourDemo(kind)
        }
    }
    // In the complete tour, each page is entered with its mark growing in the middle of the screen, while the page opens behind it.
    var introduced by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<TourKind?>(null) }
    LaunchedEffect(kind) { if (kind == null) introduced = null }
    if (kind != null && tour.chaining && introduced != kind) {
        TourTransition(
            caption = stringResource(if (kind == TourKind.HOME) R.string.tour_caption_start else R.string.tour_caption_next),
            title = stringResource(tourPageName(kind)),
            icon = { TourPageIcon(kind) },
        ) { introduced = kind }
    } else if (kind != null && onPage) {
        val next = tour.upcoming
        TourOverlay(tourSteps(kind), lastStepLabel = next?.let { stringResource(R.string.tour_next_page, stringResource(tourPageName(it))) }) { completed ->
            val wasComplete = tour.chaining
            tour.finish(completed)
            // The tour is over: a page of made-up values is left, back to where the tour was started from, and the complete tour ends on the home.
            if (tour.active.value == null && (kind.onDemoPage || wasComplete)) navController.goBack()
        }
    }
}

/** Whether [destination] is the page a tour of [kind] runs on: the real page, or the page of made-up values for a game's pages. */
private fun isPageOf(kind: TourKind, destination: androidx.navigation.NavDestination): Boolean = when (kind) {
    TourKind.HOME -> destination.hasRoute<LibraryRoute>()
    TourKind.STORE -> destination.hasRoute<StoreRoute>()
    TourKind.COMPAT_TABLE -> destination.hasRoute<CompatRoute>()
    TourKind.SETTINGS -> destination.hasRoute<SettingsRoute>()
    TourKind.STORE_GAME -> destination.isStoreGameDemo()
    TourKind.DOWNLOADS -> destination.isDownloadsDemo()
    TourKind.GAME, TourKind.GAME_COMPAT, TourKind.GAME_CONTROLLERS, TourKind.GAME_SAVES -> destination.isTourDemo(kind)
}
