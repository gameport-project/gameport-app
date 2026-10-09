package app.gameport.core.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What to do when Steam says another device plays with the account, as the game starts. Steam allows one playing session per account, and
 * the ticket a game may need cannot be given while another one plays. The game waits for the answer, as it does for a conflict of saves.
 */
@Singleton
class PlayDecisions(private val prefs: SharedPreferences?) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.getSharedPreferences("play_decisions", Context.MODE_PRIVATE))

    constructor() : this(null as SharedPreferences?)

    enum class Choice {
        /** Do not start the game. */
        QUIT,

        /** Stop the game of the other device, then play here with the ticket. */
        KICK,

        /** Start the game without its ticket. */
        PLAY,
    }

    /** A question waiting for the player, shown in front of the game. [choice] is set once they answered. */
    data class Pending(val packageName: String, val gameName: String, val choice: Choice? = null)

    private val _pending = MutableStateFlow<Map<String, Pending>>(emptyMap())

    /** The games the player chose to play without a ticket, until they close: they are not asked again at every ticket the game wants. */
    private val without = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** The open questions by package name. */
    val pending: StateFlow<Map<String, Pending>> = _pending.asStateFlow()

    /** The question is put to the player; asking again for the same game keeps the answer it has. */
    fun ask(packageName: String, gameName: String) {
        asked.add(packageName)
        _pending.update { current -> if (current[packageName] != null) current else current + (packageName to Pending(packageName, gameName)) }
    }

    fun resolve(packageName: String, choice: Choice) {
        _pending.update { current -> current[packageName]?.let { current + (packageName to it.copy(choice = choice)) } ?: current }
    }

    /** PENDING while the player has not answered, then QUIT, KICK or PLAY. An answer is given once: the question is closed when it is read. */
    fun answerFor(packageName: String): String {
        val pending = _pending.value[packageName] ?: return "NONE"
        val choice = pending.choice ?: return "PENDING"
        _pending.update { it - packageName }
        if (choice == Choice.PLAY) without.add(packageName)
        if (choice == Choice.QUIT) quit[packageName] = System.currentTimeMillis()
        return choice.name
    }

    /** Games for which the window about another device was opened during their launch: what they did then says little about the offline mode. */
    private val asked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** True, once, when that window was opened for [packageName] since the last time this was asked. */
    fun consumeAsked(packageName: String): Boolean = asked.remove(packageName)

    /** Games whose launch the player cancelled: they close at once, which says nothing about whether they work. */
    private val quit = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * True when the player cancelled the launch of [packageName] a moment ago, so that its end is not the end of a game that was played. It stays true
     * for a while rather than once: the end reaches GamePort from more than one side.
     */
    fun wasQuitRecently(packageName: String, now: Long = System.currentTimeMillis()): Boolean = quit[packageName]?.let { now - it < QUIT_MEMORY_MS } == true

    /**
     * Games for which the question is put at every start as if another device played, "all" for all of them: only the debug build can set it, to try
     * the question without one. It is kept, because the system stops GamePort a few seconds after a game starts.
     */
    private val simulated = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
        prefs?.getStringSet(SIMULATED_KEY, null)?.let { addAll(it) }
    }

    fun simulate(packageName: String, on: Boolean) {
        if (on) simulated.add(packageName) else simulated.remove(packageName)
        prefs?.edit()?.putStringSet(SIMULATED_KEY, HashSet(simulated))?.apply()
    }

    fun isSimulated(packageName: String): Boolean = ALL in simulated || packageName in simulated

    /** True when the player chose to play this game without its ticket. */
    fun playsWithout(packageName: String): Boolean = packageName in without

    /** The game gave up waiting, or left. */
    fun clear(packageName: String) {
        _pending.update { it - packageName }
        without.remove(packageName)
    }

    private companion object {
        const val SIMULATED_KEY = "simulated"
        const val ALL = "all"
        const val QUIT_MEMORY_MS = 120_000L
    }
}
