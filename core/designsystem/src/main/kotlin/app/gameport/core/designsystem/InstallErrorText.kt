package app.gameport.core.designsystem

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gameport.core.model.InstallError

/** The message for why an install stopped, the same wherever it is shown. */
@Composable
fun installErrorText(error: InstallError): String {
    val context = LocalContext.current
    return when (error) {
        is InstallError.NotEnoughSpace -> stringResource(
            R.string.game_error_space,
            Formatter.formatFileSize(context, error.neededBytes),
            Formatter.formatFileSize(context, error.freeBytes),
        )
        InstallError.NotSignedIn -> stringResource(R.string.game_error_signed_out)
        InstallError.Offline -> stringResource(R.string.game_error_offline)
        InstallError.AppUpdating -> stringResource(R.string.game_error_app_updating)
        InstallError.NoApk -> stringResource(R.string.game_error_no_apk)
        InstallError.UnreadableApk -> stringResource(R.string.game_error_unreadable)
        InstallError.VersionConflict -> stringResource(R.string.game_error_conflict)
        is InstallError.Other -> stringResource(R.string.game_install_failed, error.message.orEmpty())
    }
}
