package app.gameport.core.model

/** The Discord name a player may give with a problem report, to talk about it on the project's Discord. Optional, typed by the player, and not checked. */
object DiscordName {
    const val MAX_LENGTH = 40

    /**
     * What is kept of what was typed: no control character or angle bracket, no leading `@`, [MAX_LENGTH] characters at most. The relay cleans it the same way. Empty when
     * nothing is left, which means "no name".
     */
    fun clean(typed: String): String =
        typed.filterNot { it.isISOControl() || it == '<' || it == '>' }.trim().trimStart('@').trim().take(MAX_LENGTH).trim()
}
