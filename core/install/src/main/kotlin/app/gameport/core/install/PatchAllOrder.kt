package app.gameport.core.install

/**
 * The order "patch all" goes through the games in. The ones Android updates without asking go first: they are done without anyone doing
 * anything, and the games that need a confirmation, which hold the run until someone answers, come after. Within each group the order
 * given is kept.
 */
internal object PatchAllOrder {
    fun silentFirst(appIds: List<Int>, updatesWithoutConfirmation: (Int) -> Boolean): List<Int> {
        val (silent, asking) = appIds.partition(updatesWithoutConfirmation)
        return silent + asking
    }
}
