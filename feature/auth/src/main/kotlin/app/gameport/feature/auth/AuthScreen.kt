package app.gameport.feature.auth

import app.gameport.core.designsystem.GlassButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.GamePortLogo
import app.gameport.core.model.AuthState
import app.gameport.core.model.GuardCodeKind

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    AuthContent(
        authState = authState,
        onSignInClicked = viewModel::onSignInClicked,
        onPasswordSignIn = viewModel::onPasswordSignIn,
        onGuardCode = viewModel::onGuardCode,
        onRetryClicked = viewModel::onRetryClicked,
        onCancelClicked = viewModel::onCancelClicked,
    )
}

@Composable
internal fun AuthContent(
    authState: AuthState,
    onSignInClicked: () -> Unit,
    onPasswordSignIn: (String, String) -> Unit,
    onGuardCode: (String) -> Unit,
    onRetryClicked: () -> Unit,
    onCancelClicked: () -> Unit,
) {
    var form by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        // Scrolls, so the keyboard never hides a field; the margin is the same above and below.
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
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
                AuthState.SignedOut -> if (form) {
                    PasswordForm(
                        onSubmit = { name, password -> form = false; onPasswordSignIn(name, password) },
                        onBack = { form = false },
                    )
                } else {
                    GlassButton(onClick = onSignInClicked) {
                        ButtonLabel(Icons.Filled.QrCode2, R.string.auth_sign_in_qr)
                    }
                    GlassButton(onClick = { form = true }) {
                        ButtonLabel(Icons.Filled.Key, R.string.auth_sign_in_password)
                    }
                }
                is AuthState.AwaitingConfirmation -> {
                    Text(text = stringResource(R.string.auth_confirm_in_app))
                    QrCode(content = authState.challengeUrl, size = 240.dp)
                    CancelButton(onCancelClicked)
                }
                is AuthState.AwaitingCode -> GuardCodeForm(authState, onGuardCode, onCancelClicked)
                is AuthState.Failed -> {
                    Text(text = stringResource(R.string.auth_failed))
                    if (authState.badCredentials) {
                        Text(text = stringResource(R.string.auth_bad_credentials), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    } else {
                        authState.reason?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                    }
                    GlassButton(onClick = onRetryClicked) { ButtonLabel(Icons.Filled.Refresh, R.string.auth_retry) }
                }
                is AuthState.SignedIn -> CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ButtonLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: Int) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text = stringResource(text))
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    GlassButton(onClick = onClick) { ButtonLabel(Icons.Filled.Close, R.string.auth_cancel) }
}

/** The account name and the password. They live in this screen only, and are not saved with its state. */
@Composable
private fun PasswordForm(onSubmit: (String, String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val ready = name.isNotBlank() && password.isNotEmpty()
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.auth_account_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (ready) onSubmit(name, password) }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (visible) R.string.auth_password_hide else R.string.auth_password_show),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(text = stringResource(R.string.auth_password_kept), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        GlassButton(onClick = { onSubmit(name, password) }, enabled = ready) {
            ButtonLabel(Icons.AutoMirrored.Filled.Login, R.string.auth_submit)
        }
        GlassButton(onClick = onBack) { ButtonLabel(Icons.Filled.ArrowBack, R.string.auth_back) }
    }
}

/** The Steam Guard code, from the Steam mobile app or from an e-mail, depending on the account. */
@Composable
private fun GuardCodeForm(state: AuthState.AwaitingCode, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    // A new request for a code (the last one was refused) starts from an empty field.
    var code by remember(state) { mutableStateOf("") }
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when {
                state.kind == GuardCodeKind.APP -> stringResource(R.string.auth_code_app)
                state.emailHint.isNullOrBlank() -> stringResource(R.string.auth_code_email_unknown)
                else -> stringResource(R.string.auth_code_email, state.emailHint!!)
            },
            textAlign = TextAlign.Center,
        )
        if (state.wrongCode) {
            Text(text = stringResource(R.string.auth_code_wrong), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(CODE_LENGTH) },
            label = { Text(stringResource(R.string.auth_code_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (code.isNotEmpty()) onSubmit(code) }),
            modifier = Modifier.fillMaxWidth(),
        )
        GlassButton(onClick = { onSubmit(code) }, enabled = code.isNotEmpty()) {
            ButtonLabel(Icons.AutoMirrored.Filled.Login, R.string.auth_submit)
        }
        CancelButton(onCancel)
    }
}

/** Steam Guard codes have five characters. */
private const val CODE_LENGTH = 5
