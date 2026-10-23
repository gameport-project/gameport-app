package app.gameport.core.settings

import app.gameport.core.model.TourKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which guided tour is on screen, if any, and what is remembered of the ones already shown. */
@Singleton
class TourController @Inject constructor(private val settings: UserSettings) {
    private val _active = MutableStateFlow<TourKind?>(null)

    /** The tour on screen; the page it belongs to is opened first when it is not showing. */
    val active: StateFlow<TourKind?> = _active.asStateFlow()

    private var queue: List<TourKind> = emptyList()

    /** The complete tour is running: each page's tour is followed by the next page's. */
    var chaining: Boolean = false
        private set

    /** The page the complete tour goes to after this one, or null when this is the last or no complete tour is running. */
    val upcoming: TourKind? get() = if (chaining) queue.firstOrNull() else null

    /** Starts the tour of one page, from Settings > Help or from the help button of the page. */
    fun start(kind: TourKind) {
        chaining = false
        queue = emptyList()
        _active.value = kind
    }

    /** Starts the complete tour. It is remembered as shown from its start, so a first launch never repeats it. */
    fun startComplete() {
        settings.markHomeTourSeen()
        chaining = true
        queue = TourKind.COMPLETE.drop(1)
        _active.value = TourKind.COMPLETE.first()
    }

    /** The tour on screen ended: to its last step ([completed]) or left early. The complete tour goes on to the next page only after a completed one. */
    fun finish(completed: Boolean) {
        if (completed && chaining && queue.isNotEmpty()) {
            _active.value = queue.first()
            queue = queue.drop(1)
            return
        }
        chaining = false
        queue = emptyList()
        _active.value = null
    }

    /**
     * Told once, at the first run after the tours appeared: a player who already used GamePort ([alreadyUsed]) does not get the tour by itself, it is in
     * Settings > Help; a first install does.
     */
    fun decideFirstRun(alreadyUsed: Boolean) {
        if (settings.tourDecided) return
        settings.tourDecided = true
        if (alreadyUsed) settings.markHomeTourSeen()
    }

    /** Whether the complete tour should start now by itself: only once, and never over another tour. */
    fun firstLaunchDue(): Boolean = _active.value == null && !settings.homeTourSeen.value
}
