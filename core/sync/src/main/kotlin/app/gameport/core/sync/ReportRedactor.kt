package app.gameport.core.sync

/** Takes what identifies a person out of text that goes into a problem report. */
object ReportRedactor {
    private val steamId = Regex("""\b7656119\d{10}\b""")
    private val email = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val mac = Regex("""\b(?:[0-9A-Fa-f]{2}[:\-]){5}[0-9A-Fa-f]{2}\b""")

    /** [names] are words to hide wherever they appear (the account's display name). Names under three letters are ignored: they would hide too much. */
    fun clean(text: String, names: Collection<String> = emptyList()): String {
        var out = text
            .replace(steamId, "[steamid]")
            .replace(email, "[email]")
            .replace(mac, "[mac]")
            .replace(ipv4) { match -> if (match.value.startsWith("0.") || match.value == "127.0.0.1") match.value else "[ip]" }
        names.map { it.trim() }.filter { it.length >= 3 }.distinct().forEach { name ->
            out = out.replace(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), "[name]")
        }
        return out
    }
}
