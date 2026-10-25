package app.gameport.core.model

/**
 * Who may see adult games. Steam does not give the age of an account to the apps that ask, and a date of birth is more than needs to be known, so the
 * player is asked one thing, once: whether they are an adult. Without an answer, or with "no", adult games are hidden everywhere in the app.
 */
object AgePolicy {
    /** The age from which adult games are shown. */
    const val ADULT_AGE = 18

    /** True only when the player said they are an adult. No answer means no. */
    fun canSeeAdult(saidAdult: Boolean?): Boolean = saidAdult == true
}

/** The library without its adult games, unless the player may see them. */
fun Library.visibleTo(canSeeAdult: Boolean): Library = if (canSeeAdult) this else copy(games = games.filterNot { it.isAdult })

/** The catalog without its adult apps, unless the player may see them. */
fun AndroidCatalog.visibleTo(canSeeAdult: Boolean): AndroidCatalog = if (canSeeAdult) this else copy(apps = apps.filterNot { it.isAdult })
