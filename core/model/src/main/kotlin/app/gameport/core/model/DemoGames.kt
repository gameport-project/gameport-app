package app.gameport.core.model

/**
 * Real games, with real pictures, whose pages the guided tours dress with made-up states: a first launch has no game installed, and a page of nothing but empty
 * pictures explains little. Only the pictures and the names are theirs; every state shown (a notice, a verdict, a download) is invented and says so on the page.
 */
object DemoGames {
    data class Entry(val appId: Int, val name: String)

    val all: List<Entry> = listOf(
        Entry(345140, "Crystal Rift"),
        Entry(418650, "Space Pirate Trainer"),
        Entry(428370, "Windlands"),
        Entry(448280, "Job Simulator"),
        Entry(458580, "Windlands 2"),
    )

    /** The one whose page is shown whole. */
    val main: Entry get() = all.first()
}
