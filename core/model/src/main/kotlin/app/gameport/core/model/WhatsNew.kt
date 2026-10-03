package app.gameport.core.model

/**
 * What each GamePort version tells the player when it first opens, whether or not games have to be patched again for it: what changes,
 * and the games confirmed to work. A game is only named once the player confirmed that it works.
 */
object WhatsNew {
    enum class Item { ACHIEVEMENTS, OFFLINE, SIGN_IN }

    class Version(val code: Int, val items: List<Item>, val confirmedGames: List<String>)

    /** What several versions bring together: each item once, each game once, in the order of the versions. */
    class Summary(val items: List<Item>, val confirmedGames: List<String>) {
        val isEmpty: Boolean get() = items.isEmpty() && confirmedGames.isEmpty()
    }

    private val versions = listOf(
        Version(
            code = 700,
            items = listOf(Item.ACHIEVEMENTS, Item.OFFLINE, Item.SIGN_IN),
            confirmedGames = listOf("Moss 2", "The Last Clockwinder", "Cubism VR", "Space Pirate Trainer", "Richie's Plank Experience"),
        ),
    )

    /** The newest version that has news, as its name (700 is 0.7.0): what a preview shows. */
    val latestName: String
        get() = versions.maxOf { it.code }.let { code -> "${code / 10_000}.${code / 100 % 100}.${code % 100}" }

    /** What the versions after [seen], up to and including [current], bring. */
    fun between(seen: Int, current: Int): Summary {
        val missed = versions.filter { it.code > seen && it.code <= current }.sortedBy { it.code }
        return Summary(missed.flatMap { it.items }.distinct(), missed.flatMap { it.confirmedGames }.distinct())
    }
}
