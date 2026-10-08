package app.gameport.whatsnew

import android.content.Context
import android.util.Log
import app.gameport.core.model.Release
import app.gameport.core.model.WhatsNew
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The news of every version, read from the release files shipped in the assets (`releases/<version>.json`). A file that cannot be read is
 * left out, so one mistake never stops the others from being told.
 */
@Singleton
class WhatsNewCatalog @Inject constructor(@ApplicationContext private val context: Context) {
    val news: WhatsNew by lazy {
        val assets = context.assets
        val releases = assets.list(FOLDER).orEmpty().filter { it.endsWith(".json") }.mapNotNull { name ->
            runCatching { Release.parse(assets.open("$FOLDER/$name").bufferedReader().use { it.readText() }) }
                .onFailure { Log.w("GPWhatsNew", "release file $name not read", it) }
                .getOrNull()
        }
        WhatsNew(releases)
    }

    private companion object {
        const val FOLDER = "releases"
    }
}
