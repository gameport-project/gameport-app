package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.gameport.whatsnew.WhatsNewPreview
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * adb shell am broadcast -n app.gameport/app.gameport.debug.WhatsNewPreviewReceiver
 * Opens the news window with made-up games that have to be patched again, to see it. Nothing is patched. Add `--ez patch false` to see it
 * for a version that needs no patch, `--ez label false` to leave out the line that says it is a preview (to take a capture), and `--ez show false` to close it.
 */
@AndroidEntryPoint
class WhatsNewPreviewReceiver : BroadcastReceiver() {
    @Inject lateinit var preview: WhatsNewPreview

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("show", true)) preview.show(patchNeeded = intent.getBooleanExtra("patch", true), labelled = intent.getBooleanExtra("label", true)) else preview.hide()
    }
}
