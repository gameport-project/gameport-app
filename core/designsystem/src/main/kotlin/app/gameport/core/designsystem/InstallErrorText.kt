package app.gameport.core.designsystem

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gameport.core.model.InstallError

/**
 * The Android version a player would recognise, from the number Android itself uses: a manifest asks for API 30, and nobody has that
 * number on their headset. The ones GamePort can meet are named; anything else is given as its number rather than guessed at.
 */
private fun androidNameOf(sdk: Int): String = when (sdk) {
    29 -> "10"
    30 -> "11"
    31, 32 -> "12"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> "API $sdk"
}

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
        is InstallError.NeedsNewerAndroid -> stringResource(
            R.string.game_error_needs_newer_android,
            androidNameOf(error.requiredSdk),
            androidNameOf(error.deviceSdk),
        )
        InstallError.VersionConflict -> stringResource(R.string.game_error_conflict)
        InstallError.InstallPermission -> stringResource(R.string.game_error_install_permission)
        InstallError.NotConfirmed -> stringResource(R.string.game_error_not_confirmed)
        is InstallError.Blocked -> stringResource(R.string.game_error_blocked, error.reason?.let { " ($it)" }.orEmpty())
        // Below Android 11 there is no access to all files: what the player allows is the storage permission, so that is what is asked for.
        InstallError.ExpansionStoragePermission -> stringResource(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) R.string.game_error_expansion_storage_permission
            else R.string.game_error_expansion_storage_permission_legacy,
        )
        InstallError.ExpansionFiles -> stringResource(R.string.game_error_expansion_files)
        is InstallError.Other -> stringResource(R.string.game_install_failed, error.message.orEmpty())
    }
}
