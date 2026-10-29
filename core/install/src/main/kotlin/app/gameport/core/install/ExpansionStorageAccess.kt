package app.gameport.core.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens the page where GamePort is allowed to place the expansion files of a game. Which page that is depends on the version: Android 11
 * and later have a page of their own for the access to all files, while Android 10 grants the storage permission on the app's own page.
 */
object ExpansionStorageAccess {
    private fun appIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:${context.packageName}") }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") }
        }

    private fun generalIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        else Intent(Settings.ACTION_APPLICATION_SETTINGS)

    /** True when the system has a page where the access can be given. */
    fun settingsPageExists(context: Context): Boolean =
        appIntent(context).resolveActivity(context.packageManager) != null || generalIntent().resolveActivity(context.packageManager) != null

    /** Opens the page; nothing happens (and nothing crashes) on a device that has none. */
    fun openSettings(context: Context) {
        val opened = runCatching { context.startActivity(appIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) runCatching { context.startActivity(generalIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
