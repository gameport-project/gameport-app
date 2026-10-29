package app.gameport.core.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the page where GamePort is allowed to install apps. A headset shows this prompt outside the game, where it is easy to miss or
 * to dismiss by accident; without the permission the system never answers an install, so the page has to be reachable from the failure.
 */
object InstallSourceAccess {
    private fun appIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = Uri.parse("package:${context.packageName}") }

    private fun generalIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)

    /** True when the system has a page where the permission can be given. */
    fun settingsPageExists(context: Context): Boolean =
        appIntent(context).resolveActivity(context.packageManager) != null || generalIntent().resolveActivity(context.packageManager) != null

    /** Opens the page; nothing happens (and nothing crashes) on a device that has none. */
    fun openSettings(context: Context) {
        val opened = runCatching { context.startActivity(appIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) runCatching { context.startActivity(generalIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
