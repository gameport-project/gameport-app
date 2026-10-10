package app.gameport.feature.compat

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object CompatRoute

fun NavGraphBuilder.compatScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit) {
    composable<CompatRoute> { CompatScreen(onBack = onBack, onGameClick = onGameClick) }
}
