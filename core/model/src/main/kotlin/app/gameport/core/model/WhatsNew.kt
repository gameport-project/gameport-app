package app.gameport.core.model

/**
 * What each GamePort version tells the player when it first opens, whether or not games have to be patched again for it: what changes,
 * and the games confirmed to work. A game is only named once the player confirmed that it works. Built from the release files.
 */
class WhatsNew(releases: List<Release>) {
    private val versions = releases.sortedBy { it.code }

    /** What several versions bring together: each line once, each game once, in the order of the versions. */
    class Summary(val items: List<WindowItem>, val games: WindowGames?) {
        val isEmpty: Boolean get() = items.isEmpty() && games == null
    }

    /** The newest version that has news, as its name (0.7.0 for the code 700): what a preview shows. */
    val latestName: String get() = versions.lastOrNull()?.version.orEmpty()

    /** What the newest version brings, and only that: what a preview shows. */
    fun latest(): Summary = summary(versions.takeLast(1))

    /** What the versions after [seen], up to and including [current], bring. */
    fun between(seen: Int, current: Int): Summary = summary(versions.filter { it.code > seen && it.code <= current })

    private fun summary(missed: List<Release>): Summary {
        val tested = missed.mapNotNull { it.window.games }.flatMap { it.tested }.distinct()
        // The wording of the newest version that has a games block; the games of all of them.
        val games = missed.mapNotNull { it.window.games }.lastOrNull()?.takeIf { tested.isNotEmpty() }?.copy(tested = tested)
        return Summary(missed.flatMap { it.window.items }.distinctBy { it.id }, games)
    }
}
