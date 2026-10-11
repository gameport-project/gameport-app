package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.gameport.core.sync.AndroidCatalogRepository
import app.gameport.core.sync.CatalogTextsRepository
import app.gameport.core.sync.PriceRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * adb shell am broadcast -n app.gameport.dev/app.gameport.debug.CatalogRefreshDebugReceiver --es country fr --es lang fr --ei appId 418650
 * Asks the catalog repository for the catalog, the texts, the index of the prices, the prices of a country and the history of a game, now, and logs (tag GPCatalog)
 * what the device holds after it. It does not need the app to be on screen, which the home needs before it asks by itself.
 */
@AndroidEntryPoint
class CatalogRefreshDebugReceiver : BroadcastReceiver() {
    @Inject lateinit var catalog: AndroidCatalogRepository
    @Inject lateinit var texts: CatalogTextsRepository
    @Inject lateinit var prices: PriceRepository

    override fun onReceive(context: Context, intent: Intent) {
        val country = intent.getStringExtra("country") ?: "fr"
        val lang = intent.getStringExtra("lang") ?: "fr"
        val appId = intent.getIntExtra("appId", 418650)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                catalog.refreshIfStale()
                Log.i(TAG, "catalog: ${catalog.current?.apps?.size} apps, ${catalog.current?.apps?.count { it.androidSince != null }} with a date")
                texts.refreshIfStale(lang)
                Log.i(TAG, "texts $lang: asked")
                prices.refreshIfStale(country)
                Log.i(TAG, "index: ${prices.observeIndex().first()?.currencies?.size} countries; prices $country: ${prices.observeNow(country).first()?.all()?.size} games")
                val history = prices.refreshHistory(appId)
                Log.i(TAG, "history $appId: ${if (history == null) "nothing new (kept or still fresh)" else history.entries.joinToString { "${it.key}=${it.value.size}" }.take(80)}")
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GPCatalog"
    }
}
