package app.gameport.core.steam

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the signed-in account is for Steam's prices, as Steam tells it when GamePort logs on: the currency of its wallet (`EUR`, `USD`...) and, for an account with
 * no wallet yet, the country of the connection, which is what Steam itself goes by then. They say which country's prices to show: the ones the account pays, not
 * the ones of where the device is set. Nothing that names the account is kept, only these two codes, until a log on says others. The app logs on again each time
 * it starts, so an account already signed in has them from the next start on.
 */
@Singleton
class AccountRegion @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gameport_account_region", Context.MODE_PRIVATE)
    private val _currency = MutableStateFlow(prefs.getString(CURRENCY, null))
    private val _country = MutableStateFlow(prefs.getString(COUNTRY, null))

    /** The ISO code of the currency of the wallet, or null while it was never told (an account that never bought anything has none). */
    val currency: StateFlow<String?> = _currency.asStateFlow()

    /** The ISO code of the country Steam sees the account connect from, or null while it was never told. */
    val country: StateFlow<String?> = _country.asStateFlow()

    fun update(currency: String? = null, country: String? = null) {
        val money = currency?.takeIf { it.length == 3 && it.all(Char::isLetter) }?.uppercase()
        if (money != null && money != _currency.value) {
            _currency.value = money
            prefs.edit().putString(CURRENCY, money).apply()
        }
        val place = country?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
        if (place != null && place != _country.value) {
            _country.value = place
            prefs.edit().putString(COUNTRY, place).apply()
        }
    }

    private companion object {
        const val CURRENCY = "currency"
        const val COUNTRY = "country"
    }
}
