package app.gameport.feature.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.composable
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.model.AndroidBuild
import app.gameport.core.model.Compat
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.DemoGames
import app.gameport.core.model.ControlRef
import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.Game
import app.gameport.core.model.GameIssue
import app.gameport.core.model.GameSettings
import app.gameport.core.model.Hand
import app.gameport.core.model.InstallState
import app.gameport.core.model.Ownership
import app.gameport.core.model.Playtime
import app.gameport.core.model.RecenterMode
import app.gameport.core.model.SaveFileInfo
import app.gameport.core.model.SaveFileState
import app.gameport.core.model.SaveOverview
import app.gameport.core.model.SpeedUnit
import app.gameport.core.model.TourKind
import kotlinx.serialization.Serializable

/*
 * Pages of a game with made-up values, for the guided tours: a first launch has no game installed, and a real game shows only what applies to it, while a tour
 * must show every button and every notice. They are the real screens fed other values, so they cannot drift from the pages they explain. Nothing here reads or
 * changes a game: every action does nothing.
 */

@Serializable
data object GameDemoRoute

@Serializable
data object GameCompatDemoRoute

@Serializable
data object ControllersDemoRoute

@Serializable
data object SavesDemoRoute

/** Opens the demo page of a tour; false for a tour that has none. */
fun NavController.navigateToTourDemo(kind: TourKind): Boolean {
    when (kind) {
        TourKind.GAME -> navigate(GameDemoRoute)
        TourKind.GAME_COMPAT -> navigate(GameCompatDemoRoute)
        TourKind.GAME_CONTROLLERS -> navigate(ControllersDemoRoute)
        TourKind.GAME_SAVES -> navigate(SavesDemoRoute)
        else -> return false
    }
    return true
}

/** Whether the page on screen is the demo page of [kind]. */
fun androidx.navigation.NavDestination.isTourDemo(kind: TourKind): Boolean = when (kind) {
    TourKind.GAME -> hasRoute<GameDemoRoute>()
    TourKind.GAME_COMPAT -> hasRoute<GameCompatDemoRoute>()
    TourKind.GAME_CONTROLLERS -> hasRoute<ControllersDemoRoute>()
    TourKind.GAME_SAVES -> hasRoute<SavesDemoRoute>()
    else -> false
}

fun NavGraphBuilder.gameDemoScreens(onBack: () -> Unit) {
    composable<GameDemoRoute> { GameDemoScreen(onBack) }
    composable<GameCompatDemoRoute> { GameCompatDemoScreen(onBack) }
    composable<ControllersDemoRoute> { ControllersDemoScreen(onBack) }
    composable<SavesDemoRoute> { SavesDemoScreen(onBack) }
}

@Composable
private fun DemoNotice(modifier: Modifier = Modifier) {
    GlassChip(stringResource(R.string.demo_notice), modifier = modifier.padding(top = 16.dp, end = 32.dp))
}

/** A game that has everything: every notice at once, every button, a verdict, so the tour of the page can point at all of it. */
@Composable
private fun GameDemoScreen(onBack: () -> Unit) {
    val name = DemoGames.main.name
    val content = remember(name) {
        GameUiState.Content(
            game = Game(appId = DemoGames.main.appId, name = name, ownership = Ownership.OWNED, androidBuild = AndroidBuild(packageName = null, isVr = true)),
            install = InstallState.Installed(packageName = "demo.example"),
            issues = listOf(
                GameIssue.UpdateAvailable,
                GameIssue.PatchOutdated,
                GameIssue.ProblemSuspected(crash = false),
                GameIssue.StoragePermissionMissing,
                GameIssue.SaveConflict,
                GameIssue.SaveSyncFailed(offline = false),
                GameIssue.ControllerSilent(activated = false),
                GameIssue.ControllerMappingAvailable("valve"),
                GameIssue.ReinstallMayHelp,
                GameIssue.ExpansionMayBeMissing,
                GameIssue.ExpansionMissing(missing = 1),
            ),
            controllerProfile = true,
            controllerFrame = true,
            compatNotice = true,
            playtime = Playtime(deviceMillis = 95 * 60_000L, steamMinutes = 240),
            vrDevice = true,
            savesNotSynced = true,
            compat = Compat(CompatLevel.WORKS, "quest", works = 8, offlineOnly = 1, fails = 1, worksOffline = 0),
        )
    }
    Box {
        GameContent(
            uiState = content,
            speedUnit = SpeedUnit.MEGABYTES_PER_SECOND,
            onOpenSettings = {}, onOpenSaves = {}, onOpenControllers = {}, onOpenCompat = {},
            onActivateFrame = {}, onToggleFavorite = {}, onSetHidden = {}, onBack = onBack,
            onInstall = { _, _ -> }, onCancel = {}, onPause = {}, onVersionChosen = {}, onDuplicateChosen = {},
            onDiscard = {}, onUninstall = {}, onPlay = {}, onPatchAndPlay = {}, onOpenPermissions = {},
            onResolveConflict = {}, onRepatch = {}, onUpdate = {},
        )
        DemoNotice(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun GameCompatDemoScreen(onBack: () -> Unit) {
    Box {
        GameCompatContent(
            mapping = ControllerMapping(source = "valve", detected = demoControls()),
            settings = GameSettings(recenter = RecenterMode.AUTO),
            stageFallback = true,
            gameName = DemoGames.main.name,
            onBack = onBack,
            onUseFrameChanged = {},
            onRecenterChosen = {},
        )
        DemoNotice(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun ControllersDemoScreen(onBack: () -> Unit) {
    Box {
        ControllersContent(
            mapping = ControllerMapping(source = "valve", detected = demoControls(), enabled = true),
            gameName = DemoGames.main.name,
            onBack = onBack,
            onEnabledChanged = {},
            onTargetsChosen = { _, _ -> },
            onReset = {},
        )
        DemoNotice(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun SavesDemoScreen(onBack: () -> Unit) {
    val overview = remember {
        val now = System.currentTimeMillis()
        fun info(hours: Int, sha: String) = SaveFileInfo(size = 18_432, modifiedMillis = now - hours * 3_600_000L, sha1 = sha)
        SaveOverview(
            files = listOf(
                SaveFileState("Saves/slot1.sav", local = info(1, "a"), cloud = info(5, "b")),
                SaveFileState("Saves/settings.cfg", local = info(30, "c"), cloud = info(30, "c")),
                SaveFileState("Saves/slot2.sav", local = info(2, "d"), cloud = null),
            ),
            localSeenMillis = now - 3_600_000L,
            cloudSeenMillis = now - 7_200_000L,
            pending = null,
        )
    }
    Box {
        SavesContent(
            overview = overview,
            gameName = DemoGames.main.name,
            refreshing = false,
            offline = false,
            onBack = onBack,
            onRefresh = {}, onRestore = {}, onSend = {}, onCancelPending = {},
        )
        DemoNotice(Modifier.align(Alignment.TopEnd))
    }
}

/** The controls of a game that only knows the Steam Frame's controllers, the ones the page of the controllers lists. */
private fun demoControls(): List<ControlRef> = listOf(
    ControlRef(Hand.LEFT, "thumbstick"), ControlRef(Hand.LEFT, "trigger"), ControlRef(Hand.LEFT, "squeeze"), ControlRef(Hand.LEFT, "bumper"),
    ControlRef(Hand.LEFT, "dpad_up"), ControlRef(Hand.LEFT, "dpad_down"), ControlRef(Hand.LEFT, "menu"),
    ControlRef(Hand.RIGHT, "thumbstick"), ControlRef(Hand.RIGHT, "trigger"), ControlRef(Hand.RIGHT, "squeeze"), ControlRef(Hand.RIGHT, "bumper"),
    ControlRef(Hand.RIGHT, "a"), ControlRef(Hand.RIGHT, "b"), ControlRef(Hand.RIGHT, "x"), ControlRef(Hand.RIGHT, "y"),
)

