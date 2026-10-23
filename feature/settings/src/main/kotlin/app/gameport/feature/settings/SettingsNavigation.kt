package app.gameport.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
/** The settings open on their first category, the Steam account; [updates] opens the Updates category, for the notice of a new version. */
data class SettingsRoute(val account: Boolean = false, val updates: Boolean = false)

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onOpenGame: (Int) -> Unit) {
    composable<SettingsRoute> { entry -> SettingsScreen(onBack = onBack, onOpenGame = onOpenGame, startOnUpdates = entry.toRoute<SettingsRoute>().updates) }
}
