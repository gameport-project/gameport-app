package app.gameport.core.sync

import android.content.Context
import app.gameport.core.model.AppMedia
import app.gameport.core.model.MediaCatalog
import app.gameport.core.settings.AdultApps
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The screenshots and trailers of the store pages, read from the catalog repository (`v1/media.json`). Kept on the device and asked again when old; nothing
 * ships with the app, so a page shows no pictures until the first answer. An answer that is not a media file never replaces the one kept.
 */
@Singleton
class MediaRepository internal constructor(
    folder: File,
    transport: CatalogTransport,
    private val adult: AdultApps?,
    now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context, adult: AdultApps) : this(context.filesDir, HttpCatalogTransport(), adult)

    private val kept = KeptFile(folder, "android-media.json", "v1/media.json", transport, now, REFRESH_MS)
    private val lock = Mutex()
    private val media = MutableStateFlow(MediaCatalog.parse(kept.read()))

    /** The media of [appId], or null when it has none or is an adult app the player may not see. */
    fun observe(appId: Int): Flow<AppMedia?> {
        val hidden = adult?.hidden ?: flowOf(emptySet())
        return combine(media, hidden) { catalog, leftOut -> if (appId in leftOut) null else catalog?.of(appId) }
    }

    suspend fun refreshIfStale() {
        lock.withLock {
            kept.refreshIfStale { MediaCatalog.parse(it) != null }?.let { media.value = MediaCatalog.parse(it) }
        }
    }

    private companion object {
        const val REFRESH_MS = 3 * 60 * 60 * 1000L
    }
}
