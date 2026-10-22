package app.gameport.core.sync

/** Takes what identifies a person out of text that goes into a problem report. */
object ReportRedactor {
    private val steamId = Regex("""\b7656119\d{10}\b""")
    private val email = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    // Only the ranges a device's own network address falls in (its Wi-Fi or local address): a build number or a firmware version can look just like an
    // address (a Pico's own display string is four dot-separated numbers), and redacting every such number loses real diagnostic data for nothing.
    // One number, 0 to 255: a build or a version number often exceeds that in one place, which a plain \d{1,3} would still accept.
    private const val OCTET = """(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"""
    private val ipv4 = Regex("""\b(?:10\.$OCTET\.$OCTET\.$OCTET|172\.(?:1[6-9]|2\d|3[01])\.$OCTET\.$OCTET|192\.168\.$OCTET\.$OCTET|169\.254\.$OCTET\.$OCTET)\b""")
    private val mac = Regex("""\b(?:[0-9A-Fa-f]{2}[:\-]){5}[0-9A-Fa-f]{2}\b""")

    /** What the system and the games put in their own words: a name that is one of them would hide the words, not the person. */
    private val commonWords = setOf("true", "false", "null", "none", "default", "unknown", "android", "system", "game", "user", "debug", "error", "info")

    /** A file name with one of these endings is a file, not the end of an address (`android.hardware.graphics.common@1.2.so`). */
    private val fileEndings = listOf(".so", ".jar", ".apk", ".dex", ".odex", ".vdex", ".art", ".oat", ".prof", ".bin", ".json", ".xml", ".txt", ".cfg", ".idmap")

    /**
     * [names] are words to hide wherever they appear as a word (the account's display name): inside another word they are left, as `rue` is in
     * `true`. Names under three letters are ignored: they would hide too much, and so are the words the system uses itself.
     */
    fun clean(text: String, names: Collection<String> = emptyList()): String {
        var out = text
            .replace(steamId, "[steamid]")
            .replace(email) { match -> if (isFile(text, match)) match.value else "[email]" }
            .replace(mac, "[mac]")
            .replace(ipv4) { match -> if (match.value.startsWith("0.") || match.value == "127.0.0.1") match.value else "[ip]" }
        names.map { it.trim() }.filter { it.length >= 3 && it.lowercase() !in commonWords }.distinct().forEach { name ->
            out = out.replace(Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE), "[name]")
        }
        return out
    }

    /** Whether a match of the address pattern is a file or a path of the system: it follows a folder's slash, or ends like a file name. */
    private fun isFile(text: String, match: MatchResult): Boolean {
        val before = text.getOrNull(match.range.first - 1)
        return before == '/' || fileEndings.any { match.value.endsWith(it, ignoreCase = true) }
    }
}
