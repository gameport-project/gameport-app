package app.gameport.core.model

/** Versions of GamePort compared as numbers (0.9.10 is newer than 0.9.9); a test build (0.9.2-alpha.3) comes before its release. The same rules as the relay's. */
object VersionOrder {
    private val version = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?:-alpha\.(\d{1,5}))?$""")

    private fun parse(text: String): List<Long>? = version.matchEntire(text.trim())?.groupValues?.let { parts ->
        listOf(parts[1].toLong(), parts[2].toLong(), parts[3].toLong(), parts[4].takeIf { it.isNotEmpty() }?.toLong() ?: Long.MAX_VALUE)
    }

    /** Negative when [a] is older than [b], positive when newer, zero when the same; null when one of them is not a version. */
    fun compare(a: String, b: String): Int? {
        val x = parse(a) ?: return null
        val y = parse(b) ?: return null
        for (i in x.indices) if (x[i] != y[i]) return if (x[i] < y[i]) -1 else 1
        return 0
    }
}
