package app.gameport.feature.library

import app.gameport.core.model.AndroidCatalog
import app.gameport.core.model.StorePrice
import app.gameport.core.steam.AccountRegion
import app.gameport.core.sync.AndroidCatalogRepository
import app.gameport.core.sync.PriceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The catalog of the Steam apps that have an Android build (see AndroidCatalogRepository). */
interface AndroidCatalogSource {
    fun observe(): Flow<AndroidCatalog?>

    /** Asks for a newer catalog when the one kept is old. */
    suspend fun refreshIfStale()

    /** What every game costs now in the country that fits the account, for the label on a banner; none by default. */
    fun observePrices(): Flow<Map<Int, StorePrice>> = flowOf(emptyMap())
}

internal class RepositoryAndroidCatalogSource @Inject constructor(
    private val repository: AndroidCatalogRepository,
    private val prices: PriceRepository,
    private val accountRegion: AccountRegion,
) : AndroidCatalogSource {
    // The country whose prices fit: the currency the account is billed in, then the country Steam sees it in, then the device.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePrices(): Flow<Map<Int, StorePrice>> =
        combine(prices.observeIndex(), accountRegion.currency, accountRegion.country) { index, currency, country ->
            index?.countryFor(country ?: Locale.getDefault().country, currency)
        }.distinctUntilChanged().flatMapLatest { country ->
            if (country == null) flowOf(emptyMap()) else prices.observeNow(country).map { it?.all().orEmpty() }
        }

    override fun observe(): Flow<AndroidCatalog?> = repository.observe()

    override suspend fun refreshIfStale() {
        repository.refreshIfStale()
        // Then the prices of the country that fits the account.
        runCatching {
            val country = prices.observeIndex().first()?.countryFor(accountRegion.country.value ?: Locale.getDefault().country, accountRegion.currency.value)
            prices.refreshIfStale(country)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AndroidCatalogModule {
    @Binds
    abstract fun bindAndroidCatalogSource(impl: RepositoryAndroidCatalogSource): AndroidCatalogSource
}
