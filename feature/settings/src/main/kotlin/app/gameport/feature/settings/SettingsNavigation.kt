package app.gameport.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
/** [account] opens the Steam account page; otherwise the page that needs attention (a new version) or the account page. */
data class SettingsRoute(val account: Boolean = false)

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable<SettingsRoute> { entry -> SettingsScreen(onBack = onBack, startOnAccount = entry.toRoute<SettingsRoute>().account) }
}
