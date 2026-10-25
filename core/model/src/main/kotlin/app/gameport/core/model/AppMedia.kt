package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a store page shows besides its text: the screenshots and the trailers, as the catalog repository publishes them (see its README, `v1/media.json`).
 * The files hold only hashes and paths; the addresses are built here.
 */
@Serializable
data class AppMedia(
    /** The screenshots, in the order of the store page, each as its path under the app without the extension (`<hash>/ss_<hash>` or, for an older app, `ss_<hash>`). */
    val s: List<String> = emptyList(),
    val t: List<MediaTrailer> = emptyList(),
) {
    fun screenshotThumbnail(appId: Int, path: String): String = SteamImages.asset(appId, "$path.600x338.jpg")

    fun screenshotFull(appId: Int, path: String): String = SteamImages.asset(appId, "$path.1920x1080.jpg")

    val isEmpty: Boolean get() = s.isEmpty() && t.isEmpty()
}

/** A trailer: its name, its thumbnail, and where it plays: an adaptive stream (HLS) and a short silent loop (MP4), either of which may be missing. */
@Serializable
data class MediaTrailer(val n: String = "", val p: String = "", val h: String? = null, val m: String? = null) {
    val thumbnail: String get() = SteamImages.path(p)

    /** What to play, best first: the stream, then the loop. */
    val sources: List<String> get() = listOfNotNull(h, m).map { "$VIDEO/$it" }

    private companion object {
        const val VIDEO = "https://video.akamai.steamstatic.com/store_trailers"
    }
}

@Serializable
data class MediaCatalog(val v: Int = 1, val count: Int = 0, val apps: Map<String, AppMedia> = emptyMap()) {
    fun of(appId: Int): AppMedia? = apps[appId.toString()]?.takeUnless { it.isEmpty }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The file, or null when it cannot be read or is not a media file this version understands. */
        fun parse(text: String?): MediaCatalog? =
            runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrNull()?.takeIf { it.v == 1 }
    }
}
