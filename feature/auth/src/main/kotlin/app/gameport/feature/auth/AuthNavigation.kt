package app.gameport.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object AuthRoute

fun NavGraphBuilder.authScreen() {
    composable<AuthRoute> { AuthScreen() }
}
