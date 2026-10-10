package app.gameport.feature.compat

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object CompatRoute

/** [onGameClick] opens the page of a game of the library; [onUnownedClick] the page of the store for any other. */
fun NavGraphBuilder.compatScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit, onUnownedClick: (Int) -> Unit) {
    composable<CompatRoute> { CompatScreen(onBack = onBack, onGameClick = onGameClick, onUnownedClick = onUnownedClick) }
}
