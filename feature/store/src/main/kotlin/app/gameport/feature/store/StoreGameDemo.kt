package app.gameport.feature.store

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
import app.gameport.core.model.AndroidApp
import app.gameport.core.model.Compat
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.DemoGames
import app.gameport.core.model.SteamImages
import app.gameport.core.model.StorePrice
import kotlinx.serialization.Serializable

/*
 * The page of a store game with a made-up game, for its guided tour: a first launch may have no catalog yet, and a free game shows the button that adds it to the
 * library, which the tour must be able to point at. It is the real page fed other values, and every action does nothing.
 */

@Serializable
data object StoreGameDemoRoute

fun NavController.navigateToStoreGameDemo() = navigate(StoreGameDemoRoute)

fun NavDestination.isStoreGameDemo(): Boolean = hasRoute<StoreGameDemoRoute>()

fun NavGraphBuilder.storeGameDemoScreen(onBack: () -> Unit) {
    composable<StoreGameDemoRoute> { StoreGameDemoScreen(onBack) }
}

@Composable
private fun StoreGameDemoScreen(onBack: () -> Unit) {
    val name = DemoGames.main.name
    val summary = stringResource(R.string.demo_game_summary)
    val tags = listOf(stringResource(R.string.demo_tag_adventure), stringResource(R.string.demo_tag_vr))
    val state = remember(name, summary, tags) {
        val app = AndroidApp(
            appid = DemoGames.main.appId,
            name = name,
            vr = true,
            vrMode = "only",
            android = "ready",
            installable = true,
            release = "2025-01-15",
            modes = listOf("single"),
            languages = listOf("english", "french"),
            installBytes = 2_400_000_000L,
            downloadBytes = 1_800_000_000L,
            cover = SteamImages.asset(DemoGames.main.appId, "library_600x900.jpg"),
            banner = SteamImages.asset(DemoGames.main.appId, "library_hero.jpg"),
            header = SteamImages.asset(DemoGames.main.appId, "header.jpg"),
            capsule = SteamImages.asset(DemoGames.main.appId, "capsule_231x87.jpg"),
        )
        StoreGameUiState.Content(
            app = app,
            appId = DemoGames.main.appId,
            summary = summary,
            tags = tags,
            owned = false,
            players = Compat(CompatLevel.WORKS, "quest", works = 8, offlineOnly = 1, fails = 1, worksOffline = 0),
            price = StorePrice.Free,
            story = null,
        )
    }
    Box {
        StoreGameContent(state, onBack, onGameClick = {}, onAddToLibrary = {})
        GlassChip(stringResource(R.string.demo_notice), modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 96.dp))
    }
}

