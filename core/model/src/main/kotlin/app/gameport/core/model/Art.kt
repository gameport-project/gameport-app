package app.gameport.core.model

/**
 * A picture of a game on Steam's CDN: the one to show, then what to try, in order, when it is not there. Every picture of a game in the app is
 * shown through one of these, built here and nowhere else, so that a game whose artwork only exists under a hashed name (a demo, a playtest) is not
 * left without a cover by a screen that wrote the file name by hand.
 */
data class Art(val url: String, val fallbacks: List<String> = emptyList()) {
    /** Everything to try, without blanks or repeats. */
    val candidates: List<String> get() = (listOf(url) + fallbacks).filter { it.isNotBlank() }.distinct()

    companion object {
        /** The first of [urls] to show, the others to try after it. */
        fun of(urls: List<String>) = Art(urls.firstOrNull().orEmpty(), urls.drop(1))
    }
}

/** The pictures a store entry carries, to say which to prefer. */
enum class ArtSlot { COVER, BANNER, HEADER, CAPSULE }

/** The pictures of an app known only by its number: Steam's standard file names, which most apps have. */
object Covers {
    fun capsule(appId: Int) = Art(SteamImages.asset(appId, "library_600x900.jpg"), listOf(SteamImages.asset(appId, "header.jpg")))
}
