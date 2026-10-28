package app.gameport.core.install

import java.io.File

/**
 * What GamePort decides about a Workshop item, kept apart from the downloads so that it can be tested: where the item comes from, what is taken away, and the one line GamePort
 * keeps about an item once the game holds a whole copy (the record, which is all that stays when GamePort's own copy is deleted).
 */
internal object WorkshopPlan {
    /** The record of an item GamePort downloaded: its size on disk, Steam's date for the version it holds, its maker and its title. */
    data class Entry(val size: Long, val updated: Long, val creator: Long, val title: String)

    enum class Source { GAME_COPY, OUR_COPY, DOWNLOAD }

    /**
     * Where an item is served from. [ledger] is the record (null: never downloaded), [haveSize] the size of the game's own copy when it is whole, [ourCopy] whether GamePort still holds a
     * whole copy, [remoteUpdated] the date Steam gives for the latest version (0 when it is not known). A newer version than the record's is downloaded again; otherwise the game's copy
     * is used when it matches the record, then GamePort's own.
     */
    fun decide(ledger: Entry?, haveSize: Long?, ourCopy: Boolean, remoteUpdated: Long): Source {
        if (ledger == null) return Source.DOWNLOAD
        if (remoteUpdated > 0 && ledger.updated > 0 && remoteUpdated > ledger.updated) return Source.DOWNLOAD
        if (haveSize != null && haveSize == ledger.size && ledger.size > 0) return Source.GAME_COPY
        if (ourCopy) return Source.OUR_COPY
        return Source.DOWNLOAD
    }

    /** The recorded items to take away: those the account is no longer subscribed to and the game does not ask for, and only when the subscriptions were read. */
    fun toForget(held: Set<Long>, wanted: Set<Long>, subscriptionsKnown: Boolean): Set<Long> = if (subscriptionsKnown) held - wanted else emptySet()

    fun read(file: File): Entry? = runCatching {
        val parts = file.readText().trimEnd('\n').split('\t', limit = 4)
        if (parts.size < 4) null else Entry(parts[0].toLong(), parts[1].toLong(), parts[2].toLong(), parts[3])
    }.getOrNull()

    fun write(file: File, entry: Entry) {
        file.parentFile?.mkdirs()
        val title = entry.title.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        file.writeText("${entry.size}\t${entry.updated}\t${entry.creator}\t$title\n")
    }
}
