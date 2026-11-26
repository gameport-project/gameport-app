package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.ControllerAnswer
import app.gameport.core.model.ControllerFeedbackBook
import app.gameport.core.model.ControllerFingerprint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * What the player said about the controls of each game, kept on this device: the answer and the mapping it was given for, when the mapping last changed, when a run of the
 * game last ended, and what was put off. The rules are in [ControllerFeedbackBook]; this keeps it.
 */
@Singleton
class ControllerFeedbackStore @Inject constructor(
    @ApplicationContext context: Context,
    private val mappings: ControllerMappingStore,
) {
    private val prefs = context.getSharedPreferences("gameport_controller_feedback", Context.MODE_PRIVATE)
    private val _book = MutableStateFlow(ControllerFeedbackBook.fromJson(prefs.getString(BOOK, null)))

    val book: StateFlow<ControllerFeedbackBook> = _book.asStateFlow()

    @Synchronized
    fun update(change: (ControllerFeedbackBook) -> ControllerFeedbackBook) {
        val updated = change(_book.value)
        if (updated == _book.value) return
        prefs.edit().putString(BOOK, updated.toJson()).apply()
        _book.value = updated
    }

    /** The mapping the game has now. */
    fun fingerprint(appId: Int): ControllerFingerprint = ControllerFingerprint.of(mappings.get(appId))

    /** Looks at the mapping of the game: when it is not the one kept, now is the time it changed. */
    fun noteCurrent(appId: Int) {
        val mapping = fingerprint(appId).mapping
        update { it.noted(appId, mapping, System.currentTimeMillis()) }
    }

    /**
     * A run of the game ended. A mapping that changed without the player touching it (the game's first run tells which controls it uses) changed before the run, so it
     * counts as seen before it, not after.
     */
    fun sessionEnded(appId: Int) {
        val mapping = fingerprint(appId).mapping
        update { it.noted(appId, mapping, 0L).sessionEnded(appId, System.currentTimeMillis()) }
    }

    /** Whether the card that asks about the controls of the game is shown. */
    fun observeAsk(appId: Int): Flow<Boolean> = combine(_book, mappings.observe(appId)) { book, mapping ->
        // Only where the controls are put on the device's controllers and the game's controls are known; GamePort's own mapping is judged as well as the player's.
        mapping.translated && mapping.detected.isNotEmpty() && book.shouldAsk(appId, ControllerFingerprint.of(mapping), System.currentTimeMillis())
    }

    /** The answer the player gave for the mapping the game has now, if it is their last one. */
    fun observeAnswer(appId: Int): Flow<ControllerAnswer?> = combine(_book, mappings.observe(appId)) { book, mapping ->
        book.answerFor(appId, ControllerFingerprint.of(mapping).mapping)
    }

    /** The player answered for the mapping the game has now. */
    fun answer(appId: Int, answer: ControllerAnswer) {
        val current = fingerprint(appId)
        update { it.noted(appId, current.mapping, System.currentTimeMillis()).answered(appId, current, answer) }
    }

    /** The player said "later" for the mapping the game has now. */
    fun later(appId: Int) {
        val current = fingerprint(appId)
        update { it.later(appId, current) }
    }

    private companion object {
        const val BOOK = "book"
    }
}
