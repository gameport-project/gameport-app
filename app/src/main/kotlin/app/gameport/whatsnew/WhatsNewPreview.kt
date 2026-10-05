package app.gameport.whatsnew

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shows the news window with made-up games, to see it without having games to patch. Nothing is patched and nothing is remembered. Only the
 * debug builds have a way to turn it on (see the debug receiver); in a release build it stays off. The value says whether the made-up games
 * need patching (the red alert and the buttons) or not.
 */
@Singleton
class WhatsNewPreview @Inject constructor() {
    private val _mode = MutableStateFlow<Boolean?>(null)
    val mode: StateFlow<Boolean?> = _mode.asStateFlow()

    /** Whether the window says it is a preview: a capture to publish does without that line. */
    var labelled: Boolean = true
        private set

    fun show(patchNeeded: Boolean, labelled: Boolean = true) {
        this.labelled = labelled
        _mode.value = patchNeeded
    }

    fun hide() {
        _mode.value = null
    }
}

/** The window postponed with "see later": it comes back the next time GamePort is opened, not before. Lives as long as the process. */
@Singleton
class WhatsNewPostponed @Inject constructor() {
    private val _value = MutableStateFlow(false)
    val value: StateFlow<Boolean> = _value.asStateFlow()

    fun postpone() {
        _value.value = true
    }
}
