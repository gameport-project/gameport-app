package app.gameport.core.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

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
