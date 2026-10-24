package app.gameport.core.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** What the game said about its expansion files, for the build it was said about. */
@Serializable
data class ExpansionVerdict(
    val state: State,
    /** The manifests of the build that was checked: a verdict is not valid for another build. */
    val manifests: Map<Int, Long>,
    /** The files the game lacks and how many Steam lists, when [state] is [State.MISSING]. */
    val missing: Int = 0,
    val expected: Int = 0,
    /** When the game was last installed or patched: a game that could not answer is asked again only once that changes, so its process is not woken at every start. */
    val packageUpdatedAt: Long = 0L,
) {
    @Serializable
    enum class State {
        /** Every file Steam lists is there, by size. */
        COMPLETE,

        /** Some files Steam lists are not. */
        MISSING,

        /** The game did not answer (a hook from before this existed) or its folder could not be listed: nothing is known. */
        UNKNOWN,
    }
}

/** Remembers what each game said, so a game is asked once per build and not at every start. */
@Singleton
class ExpansionVerdicts @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "expansion-verdicts.json")
    private val serializer = MapSerializer(Int.serializer(), ExpansionVerdict.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val _all = MutableStateFlow(read())

    val all: StateFlow<Map<Int, ExpansionVerdict>> = _all.asStateFlow()

    @Synchronized
    fun put(appId: Int, verdict: ExpansionVerdict) = write(_all.value + (appId to verdict))

    /** The files were put in place again: what was said before no longer holds. */
    @Synchronized
    fun forget(appId: Int) = write(_all.value - appId)

    private fun read(): Map<Int, ExpansionVerdict> = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

    private fun write(map: Map<Int, ExpansionVerdict>) {
        _all.value = map
        runCatching { file.writeText(json.encodeToString(serializer, map)) }
    }
}
