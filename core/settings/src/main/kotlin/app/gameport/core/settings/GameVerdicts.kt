package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.Verdict
import app.gameport.core.model.VerdictBook
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the player said about each game ("it worked" / "it did not"), kept on this device. The rules are in [VerdictBook]; this keeps it, and
 * the random number that tells two players apart to the relay without telling who they are.
 */
@Singleton
class GameVerdicts @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gameport_verdicts", Context.MODE_PRIVATE)
    private val _book = MutableStateFlow(VerdictBook.fromJson(prefs.getString(BOOK, null)))
    private val _share = MutableStateFlow(prefs.getBoolean(SHARE, true))

    val book: StateFlow<VerdictBook> = _book.asStateFlow()

    /** The player lets the answers go to the relay (see the README of the relay for what is sent). On by default. */
    val share: StateFlow<Boolean> = _share.asStateFlow()

    fun setShare(share: Boolean) {
        prefs.edit().putBoolean(SHARE, share).apply()
        _share.value = share
    }

    /** A random number made once, kept on this device, never shown: the relay only ever sees a salted hash of it. */
    val voterId: String
        @Synchronized get() = prefs.getString(VOTER, null)?.takeIf { it.length == 32 } ?: newVoterId().also { prefs.edit().putString(VOTER, it).apply() }

    @Synchronized
    fun update(change: (VerdictBook) -> VerdictBook) {
        val updated = change(_book.value)
        if (updated == _book.value) return
        prefs.edit().putString(BOOK, updated.toJson()).apply()
        _book.value = updated
    }

    /** [holdForReport]: the answer waits to say what became of its report (see [VerdictBook.reportStated]), so the report goes first. */
    fun answer(appId: Int, verdict: Verdict, appVersion: String, today: String, holdForReport: Boolean = false) =
        update { it.answered(appId, verdict, appVersion, today, send = _share.value, holdForReport = holdForReport) }

    /** What became of the report of [appId], one of [app.gameport.core.model.ReportStates] or null: the answer goes, or goes again, with it. */
    fun reportStated(appId: Int, state: String?) = update { it.reportStated(appId, state, send = _share.value) }

    /** Answers that waited for a report of an earlier run of GamePort are let go. */
    fun releaseHolds() = update { it.releaseHolds() }

    /** A problem reported by hand counts as "it did not work" (see [VerdictBook.reportedProblem]). */
    fun reportedProblem(appId: Int, appVersion: String, today: String) = update { it.reportedProblem(appId, appVersion, today, send = _share.value) }

    /** What the player ticked as going wrong with the answer they gave for [appId]: it goes to the relay with that answer (see [VerdictBook.withIssues]). */
    fun setIssues(appId: Int, symptoms: Set<app.gameport.core.model.Symptom>) = update { it.withIssues(appId, symptoms, send = _share.value) }

    private fun newVoterId(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private companion object {
        const val BOOK = "book"
        const val SHARE = "share"
        const val VOTER = "voter"
    }
}
