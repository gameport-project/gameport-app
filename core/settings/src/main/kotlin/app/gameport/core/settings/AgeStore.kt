package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.AgePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The answer to the one question the player is asked, whether they are an adult, kept on the device and nowhere else. It only decides whether adult
 * games are shown (see [AgePolicy]).
 */
@Singleton
class AgeStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _said = MutableStateFlow(read())
    private val _canSeeAdult = MutableStateFlow(AgePolicy.canSeeAdult(_said.value))

    /** What the player said: yes, no, or null while the question was not answered. */
    val said: StateFlow<Boolean?> = _said.asStateFlow()

    /** True when the player said they are an adult. Adult games are hidden everywhere while this is false. */
    val canSeeAdult: StateFlow<Boolean> = _canSeeAdult.asStateFlow()

    fun answer(adult: Boolean) {
        prefs.edit().putBoolean(KEY_ADULT, adult).apply()
        _said.value = adult
        _canSeeAdult.value = AgePolicy.canSeeAdult(adult)
    }

    private fun read(): Boolean? = if (prefs.contains(KEY_ADULT)) prefs.getBoolean(KEY_ADULT, false) else null

    private companion object {
        const val PREFS = "gameport_age"
        const val KEY_ADULT = "adult"
    }
}
