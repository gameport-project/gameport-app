package app.gameport.feature.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.model.AndroidBuild
import app.gameport.core.model.DemoGames
import app.gameport.core.model.Game
import app.gameport.core.model.InstallError
import app.gameport.core.model.InstallState
import app.gameport.core.model.Ownership
import app.gameport.core.model.SpeedUnit
import kotlinx.serialization.Serializable

/*
 * The page of the downloads with made-up games in every state, for its guided tour: a first launch has nothing downloading, and the tour must show what a line
 * can look like. It is the real page fed other values, and every action does nothing.
 */

@Serializable
data object DownloadsDemoRoute

fun NavController.navigateToDownloadsDemo() = navigate(DownloadsDemoRoute)

fun NavDestination.isDownloadsDemo(): Boolean = hasRoute<DownloadsDemoRoute>()

fun NavGraphBuilder.downloadsDemoScreen(onBack: () -> Unit) {
    composable<DownloadsDemoRoute> { DownloadsDemoScreen(onBack) }
}

@Composable
private fun DownloadsDemoScreen(onBack: () -> Unit) {
    val entries = remember {
        fun game(index: Int) = DemoGames.all[index].let { Game(appId = it.appId, name = it.name, ownership = Ownership.OWNED, androidBuild = AndroidBuild(packageName = null, isVr = true)) }
        val states = listOf<InstallState>(
            InstallState.Downloading(progress = 0.42f, bytesPerSecond = 18_000_000L),
            InstallState.Queued,
            InstallState.Failed(InstallError.Offline),
            InstallState.Installed("demo.one"),
            InstallState.Installed("demo.two"),
        )
        states.mapIndexed { index, state -> DownloadEntry(DemoGames.all[index].appId, game(index), state, installedAt = if (state is InstallState.Installed) (5 - index).toLong() else null) }
    }
    Box {
        DownloadsContent(
            entries = entries,
            speedUnit = SpeedUnit.MEGABYTES_PER_SECOND,
            // Two games with a new version, so the line that updates them all shows.
            updatable = setOf(DemoGames.all[3].appId, DemoGames.all[4].appId),
            onBack = onBack,
            onGameClick = {},
            onUpdate = {}, onCancel = {}, onPause = {}, onDiscard = {}, onUninstall = {}, onResume = {}, onPlay = {},
        )
        GlassChip(stringResource(R.string.demo_notice), modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 96.dp))
    }
}

