package app.gameport.core.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The games whose player said they work, to the question "does the game work?" that goes with the notice about expansion files named after a raised version.
 * Once said, the question and its mark are gone for that game. Installing the game again from scratch (which settles it) makes the question moot by itself.
 */
@Singleton
class ReinstallNoticeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_reinstall_notice", Context.MODE_PRIVATE)
    private val _answered = MutableStateFlow(prefs.all.keys.mapNotNull { it.toIntOrNull() }.toSet())

    /** The games whose player said they work. */
    val answered: StateFlow<Set<Int>> = _answered.asStateFlow()

    fun answerWorks(appId: Int) {
        prefs.edit().putBoolean(appId.toString(), true).apply()
        _answered.value = _answered.value + appId
    }

    /** The game was removed or installed again: a later question starts afresh. */
    fun forget(appId: Int) {
        prefs.edit().remove(appId.toString()).apply()
        _answered.value = _answered.value - appId
    }
}
