package app.gameport.core.model

/** What GamePort is doing with one extra content (DLC) of an installed game: fetching it ([removing] false), taking it off, or what went wrong ([error]). */
data class DlcWork(
    val dlcAppId: Int,
    val removing: Boolean = false,
    /** 0..1 of the download while it runs. */
    val progress: Float = 0f,
    val error: String? = null,
)

/**
 * One extra content chosen for the game that is being installed. [withGame]: its files are in the game's own download, so it is in place when the game is; otherwise it is fetched
 * on its own once the game is installed, one after the other, in the order of the list.
 */
data class ExtraPlanItem(val appId: Int, val name: String, val withGame: Boolean)

/** One extra content of a game the account has: [installed] when it is on the device, [removable] when GamePort can take it off alone. */
data class ExtraContentItem(
    val appId: Int,
    val name: String,
    val downloadBytes: Long,
    val installBytes: Long,
    val installed: Boolean,
    val removable: Boolean,
)

/** The extra content of an installed game, as its page shows it. */
data class ExtraContent(val items: List<ExtraContentItem> = emptyList(), val work: DlcWork? = null) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        /**
         * The extra content the account has for [dlc] (only what it owns is offered), with what is installed ([installed], and among those [removable], the ones GamePort
         * placed alone: the ones installed with the game share its files). Sorted by name.
         */
        fun of(dlc: List<DlcContent>, installed: Set<Int>, removable: Set<Int>, work: DlcWork?): ExtraContent = ExtraContent(
            items = dlc.filter { it.owned && it.depots.isNotEmpty() }.map { content ->
                ExtraContentItem(
                    appId = content.appId,
                    name = content.name,
                    downloadBytes = content.depots.sumOf { it.downloadBytes },
                    installBytes = content.depots.sumOf { it.installBytes },
                    installed = content.appId in installed,
                    removable = content.appId in removable,
                )
            }.sortedBy { it.name.lowercase() },
            work = work,
        )
    }
}
