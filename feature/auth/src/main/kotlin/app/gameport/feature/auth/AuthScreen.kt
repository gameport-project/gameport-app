package app.gameport.feature.auth

import app.gameport.core.designsystem.GlassButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.GamePortLogo
import app.gameport.core.model.AuthState

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    AuthContent(
        authState = authState,
        onSignInClicked = viewModel::onSignInClicked,
        onCancelClicked = viewModel::onCancelClicked,
    )
}

@Composable
internal fun AuthContent(authState: AuthState, onSignInClicked: () -> Unit, onCancelClicked: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GamePortLogo(Modifier.size(104.dp))
            Text(text = stringResource(R.string.auth_title), style = MaterialTheme.typography.displaySmall)
            when (authState) {
                AuthState.Connecting -> {
                    CircularProgressIndicator()
                    Text(text = stringResource(R.string.auth_connecting))
                }
                AuthState.SignedOut -> Button(onClick = onSignInClicked) {
                    Text(text = stringResource(R.string.auth_sign_in))
                }
                is AuthState.AwaitingConfirmation -> {
                    Text(text = stringResource(R.string.auth_confirm_in_app))
                    QrCode(content = authState.challengeUrl, size = 240.dp)
                    GlassButton(onClick = onCancelClicked) {
                        Text(text = stringResource(R.string.auth_cancel))
                    }
                }
                is AuthState.Failed -> {
                    Text(text = stringResource(R.string.auth_failed))
                    authState.reason?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onSignInClicked) { Text(text = stringResource(R.string.auth_retry)) }
                }
                is AuthState.SignedIn -> CircularProgressIndicator()
            }
        }
    }
}
