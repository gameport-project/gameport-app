package app.gameport.core.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** When the player is asked for access to all files before a game with expansion files is installed. Pure, so it can be tried without a device. */
object ExpansionAccessRule {
    /**
     * True when the download holds expansion files, the system has the special access (Android 11 and later), GamePort does not have it yet, and the system has a
     * page where it can be given: a device without that page is not stopped, the files are tried anyway. Once given, the access stays: nothing is asked again.
     */
    fun mustAsk(hasExpansion: Boolean, sdk: Int, hasAccess: Boolean, settingsPageExists: Boolean): Boolean =
        hasExpansion && sdk >= ANDROID_11 && !hasAccess && settingsPageExists

    private const val ANDROID_11 = 30
}

/** Opens Android's special-access page for GamePort, so it can place the expansion files of a game. */
object ExpansionStorageAccess {
    private fun appIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:${context.packageName}") }

    private fun generalIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    /** True when the system has a page where the access can be given. */
    fun settingsPageExists(context: Context): Boolean =
        appIntent(context).resolveActivity(context.packageManager) != null || generalIntent().resolveActivity(context.packageManager) != null

    /** Opens the page; nothing happens (and nothing crashes) on a device that has none. */
    fun openSettings(context: Context) {
        val opened = runCatching { context.startActivity(appIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) runCatching { context.startActivity(generalIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
