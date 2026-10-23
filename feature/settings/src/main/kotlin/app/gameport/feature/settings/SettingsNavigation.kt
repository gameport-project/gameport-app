package app.gameport.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
/** The settings open on their first category, the Steam account; [account] is kept for the links that ask for it by name. */
data class SettingsRoute(val account: Boolean = false)

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable<SettingsRoute> { SettingsScreen(onBack = onBack) }
}
