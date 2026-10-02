package app.gameport.core.model

/**
 * One achievement of a game, as Steam describes it. [icon] and [iconGray] are file names Steam publishes
 * (see [AchievementArtwork]), [unlockedAt] is in seconds since 1970 and 0 when Steam does not know when.
 */
data class Achievement(
    val name: String,
    val title: String,
    val description: String,
    val icon: String?,
    val iconGray: String?,
    val hidden: Boolean,
    val unlocked: Boolean,
    val unlockedAt: Long = 0L,
)

/** The achievements of a game for the signed-in account, texts in [language] (a Steam language name, see [SteamLanguage]). */
data class AchievementList(
    val appId: Int,
    val language: String,
    val items: List<Achievement>,
) {
    val total: Int get() = items.size
    val unlockedCount: Int get() = items.count { it.unlocked }

    /** The latest unlocked ones, the most recent first. One whose time Steam does not know comes last. */
    fun recent(count: Int): List<Achievement> = items.filter { it.unlocked }.sortedByDescending { it.unlockedAt }.take(count)

    /** Unlocked first (the most recent first), then the ones left to unlock in the order the game lists them. */
    fun ordered(): List<Achievement> = items.filter { it.unlocked }.sortedByDescending { it.unlockedAt } + items.filter { !it.unlocked }
}

/** Where the pictures of a game's achievements are, tried in order. */
object AchievementArtwork {
    private const val PATH = "steamcommunity/public/images/apps"
    private val hosts = listOf("https://cdn.fastly.steamstatic.com", "https://steamcdn-a.akamaihd.net")

    fun urls(appId: Int, file: String?): List<String> =
        if (file.isNullOrBlank()) emptyList() else hosts.map { "$it/$PATH/$appId/$file" }
}

/** The names Steam gives its languages, from the language of the device. */
object SteamLanguage {
    private val byCode = mapOf(
        "ar" to "arabic", "bg" to "bulgarian", "cs" to "czech", "da" to "danish", "nl" to "dutch", "en" to "english",
        "fi" to "finnish", "fr" to "french", "de" to "german", "el" to "greek", "hu" to "hungarian", "id" to "indonesian",
        "it" to "italian", "ja" to "japanese", "ko" to "koreana", "no" to "norwegian", "nb" to "norwegian", "nn" to "norwegian",
        "pl" to "polish", "ro" to "romanian", "ru" to "russian", "sv" to "swedish", "th" to "thai", "tr" to "turkish",
        "uk" to "ukrainian", "vi" to "vietnamese",
    )

    /** [language] is an ISO 639 code (`fr`), [country] and [script] are what the locale carries, possibly empty. English when Steam has no such language. */
    fun of(language: String, country: String = "", script: String = ""): String = when (language.lowercase()) {
        "pt" -> if (country.equals("BR", true)) "brazilian" else "portuguese"
        // Steam has two Spanishes: Spain's, and the Latin American one for every other country.
        "es" -> if (country.isNotEmpty() && !country.equals("ES", true)) "latam" else "spanish"
        "zh" -> if (script.equals("Hant", true) || country.uppercase() in setOf("TW", "HK", "MO")) "tchinese" else "schinese"
        else -> byCode[language.lowercase()] ?: "english"
    }
}
