package app.gameport.core.sync

/** What the player wrote about the problem, as the report keeps it: nothing when it is blank, and never more than [MAX] characters, so it cannot crowd the report out. */
object ReportDescription {
    const val MAX = 2000

    fun of(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX)
}
