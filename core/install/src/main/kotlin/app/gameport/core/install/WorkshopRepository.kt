package app.gameport.core.install

import android.content.Context
import android.os.Environment
import android.util.Log
import app.gameport.core.steam.GameDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

/**
 * The Workshop items of a game (puzzles made by players, for instance), which the game asks Steam for through its Workshop interface. The game cannot reach Steam here, so
 * the hook relays what it asks (see the `workshop` call of the provider): each item is looked up in Steam's public records and downloaded by GamePort with the signed-in
 * account into GamePort's own folder. The hook then copies it into the game's own folder (the game cannot enter a folder GamePort makes), and as soon as the game holds a whole
 * copy GamePort deletes its own and keeps one line of record (see [WorkshopPlan]): an item is on the device once.
 *
 * What the account is subscribed to is followed, and an item it is no longer subscribed to is taken away (from GamePort and, through the hook, from the game) only when the list of
 * subscriptions was read: an error of the network never empties anything. An item Steam has updated is downloaded again. Nothing is written to the Steam account.
 */
@Singleton
class WorkshopRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: InstalledGames,
    private val downloader: GameDownloader,
    private val events: GameEventLog,
) {
    enum class Phase { QUEUED, DOWNLOADING, INSTALLED, FAILED }

    /** What the game is told about one item. [sizeOnDisk] counts once installed; [folder] is where its files are. */
    data class Item(
        val id: Long,
        val phase: Phase,
        val fraction: Float = 0f,
        val sizeOnDisk: Long = 0L,
        val folder: String = "",
        val title: String = "",
        val creator: Long = 0L,
        val updated: Long = 0L,
        val error: String = "",
    )

    /** What one poll of the hook gets: the items, and whether the list of subscriptions was read (the hook only takes items away when it was). */
    class Answer(val items: List<Item>, val subscriptionsKnown: Boolean)

    private data class Details(val title: String, val creator: Long, val consumerApp: Int, val updated: Long, val manifest: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val limit = Semaphore(2)
    private val states = ConcurrentHashMap<String, Item>()
    private val failures = ConcurrentHashMap<String, Long>()
    private val details = ConcurrentHashMap<Long, Details>()

    private fun key(packageName: String, id: Long) = "$packageName/$id"

    /**
     * GamePort keeps the items in its own folder. They were in the game's expansion folder first, but a folder GamePort makes there cannot be entered by the game (only files are
     * open to all): the game saw the path and nothing behind it. The hook now copies each item into the game's own folder, from the files GamePort serves it.
     */
    private fun root(packageName: String) = File(context.getExternalFilesDir("workshop") ?: File(context.filesDir, "workshop"), packageName)

    /** The folder GamePort made in the game's expansion folder before the items were kept in its own: taken away. */
    private fun removeOldFolder(packageName: String) {
        runCatching { File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName/gp_workshop").deleteRecursively() }
    }

    /** The files of an installed item as (path under its folder, size), for the hook to copy; empty when the item is not whole. */
    fun filesOf(packageName: String, id: Long): List<Pair<String, Long>> {
        val folder = File(root(packageName), id.toString())
        if (!folder.isDirectory || WorkshopPlan.read(ledgerFile(packageName, id)) == null) return emptyList()
        return folder.walkTopDown().filter { it.isFile }.map { it.relativeTo(folder).path.replace('\\', '/') to it.length() }.toList()
    }

    /** One file of an installed item, only when it is inside the item's folder. */
    fun fileOf(packageName: String, id: Long, relative: String): File? {
        val folder = File(root(packageName), id.toString()).canonicalFile
        val file = File(folder, relative).canonicalFile
        return file.takeIf { it.isFile && it.path.startsWith(folder.path + File.separator) && WorkshopPlan.read(ledgerFile(packageName, id)) != null }
    }

    private fun ledgerFile(packageName: String, id: Long) = File(root(packageName), "$id.ledger")

    private fun appOf(packageName: String): Int? = installed.all().entries.firstOrNull { it.value == packageName }?.key

    /**
     * The state of each item the game asked for or the account is subscribed to. [have] is what the game holds already: the size of each item whose copy in its folder is whole.
     * An item not seen before starts downloading.
     */
    suspend fun request(packageName: String, ids: List<Long>, have: Map<Long, Long> = emptyMap()): Answer {
        if (!oldFolderRemoved.contains(packageName)) { oldFolderRemoved += packageName; removeOldFolder(packageName) }
        val subscribed = subscribedTo(packageName)
        val wanted = (ids + subscribed.first).distinct().take(MAX_ITEMS)
        forgetUnwanted(packageName, wanted.toSet(), subscribed.second)
        return Answer(wanted.map { id -> state(packageName, id, have[id]) }, subscribed.second)
    }

    private val oldFolderRemoved = ConcurrentHashMap.newKeySet<String>()

    private val subscriptions = ConcurrentHashMap<String, Triple<Long, List<Long>, Boolean>>()

    /** What the account is subscribed to for this game, and whether Steam answered. Subscribing is done on Steam (the site, or the application), and GamePort follows. Read at most every 20 seconds. */
    private suspend fun subscribedTo(packageName: String): Pair<List<Long>, Boolean> {
        val known = subscriptions[packageName]
        if (known != null && System.currentTimeMillis() - known.first < SUBSCRIPTIONS_EVERY_MS) return known.second to known.third
        // Several records can point at one package (a demo and its game): the game of the Workshop is the one the items name; all are asked.
        val answers = installed.all().entries.filter { it.value == packageName }.map { downloader.subscribedWorkshopItems(it.key) }
        val read = answers.flatMap { it.orEmpty() }.distinct()
        val complete = answers.isNotEmpty() && answers.all { it != null }
        subscriptions[packageName] = Triple(System.currentTimeMillis(), read, complete)
        return read to complete
    }

    /** An item the account left, and the game does not ask for, is taken away: its copy and its record. Never when the subscriptions could not be read. */
    private fun forgetUnwanted(packageName: String, wanted: Set<Long>, subscriptionsKnown: Boolean) {
        val held = root(packageName).listFiles().orEmpty().filter { it.name.endsWith(".ledger") }.mapNotNull { it.name.removeSuffix(".ledger").toLongOrNull() }.toSet()
        for (id in WorkshopPlan.toForget(held, wanted, subscriptionsKnown)) {
            File(root(packageName), id.toString()).deleteRecursively()
            ledgerFile(packageName, id).delete()
            states.remove(key(packageName, id))
            appOf(packageName)?.let { events.note(it, "workshop item $id taken away: the account is no longer subscribed to it") }
        }
    }

    private val checkedForUpdate = ConcurrentHashMap.newKeySet<String>()

    private fun state(packageName: String, id: Long, haveSize: Long?): Item {
        val k = key(packageName, id)
        states[k]?.let { known ->
            // A failed item is tried again, but not at every poll of the game.
            if (known.phase == Phase.FAILED && System.currentTimeMillis() - (failures[k] ?: 0L) >= RETRY_AFTER_MS) { /* tried again below */ }
            else if (known.phase != Phase.INSTALLED) return known
        }
        val folder = File(root(packageName), id.toString())
        val ledger = WorkshopPlan.read(ledgerFile(packageName, id))
        // Whether Steam has a newer version is asked once, in the background, the first time an installed item is seen after GamePort starts.
        if (ledger != null && checkedForUpdate.add(k)) scope.launch { lookup(id) }
        val ourCopy = folder.isDirectory && ledger != null && sizeOf(folder) == ledger.size
        return when (WorkshopPlan.decide(ledger, haveSize, ourCopy, details[id]?.updated ?: 0L)) {
            WorkshopPlan.Source.GAME_COPY -> {
                // The game holds a whole copy: GamePort's own is useless and goes.
                if (folder.exists()) {
                    folder.deleteRecursively()
                    appOf(packageName)?.let { events.note(it, "workshop item $id: the game holds it whole, GamePort's own copy is deleted (${ledger!!.size / 1024} KB freed)") }
                }
                Item(id, Phase.INSTALLED, 1f, ledger!!.size, "", ledger.title, ledger.creator, ledger.updated).also { states[k] = it }
            }
            WorkshopPlan.Source.OUR_COPY -> Item(id, Phase.INSTALLED, 1f, ledger!!.size, folder.path, ledger.title, ledger.creator, ledger.updated).also { states[k] = it }
            WorkshopPlan.Source.DOWNLOAD -> {
                if (states[k]?.phase == Phase.DOWNLOADING || states[k]?.phase == Phase.QUEUED) return states[k]!!
                val queued = Item(id, Phase.QUEUED, folder = folder.path)
                states[k] = queued
                start(packageName, id, folder)
                queued
            }
        }
    }

    private fun start(packageName: String, id: Long, folder: File) {
        val k = key(packageName, id)
        scope.launch {
            limit.withPermit {
                try {
                    val detail = lookup(id)
                    val appId = detail?.consumerApp?.takeIf { it > 0 } ?: appOf(packageName) ?: error("The game of item $id is not known")
                    val part = File(root(packageName), "$id.part")
                    part.deleteRecursively()
                    root(packageName).mkdirs()
                    appOf(packageName)?.let { events.note(it, "workshop item $id (${detail?.title.orEmpty()}) requested by the game") }
                    states[k] = Item(id, Phase.DOWNLOADING, 0f, 0L, folder.path, detail?.title.orEmpty(), detail?.creator ?: 0L, detail?.updated ?: 0L)
                    downloader.downloadWorkshopItem(appId, id, detail?.manifest ?: 0L, part) { fraction ->
                        states[k]?.let { states[k] = it.copy(phase = Phase.DOWNLOADING, fraction = fraction.coerceIn(0f, 1f)) }
                    }
                    check(part.isDirectory && part.walkTopDown().any { it.isFile }) { "Steam gave no file for this item" }
                    folder.deleteRecursively()
                    ledgerFile(packageName, id).delete()
                    check(part.renameTo(folder)) { "Could not put the item in place" }
                    val size = sizeOf(folder)
                    WorkshopPlan.write(ledgerFile(packageName, id), WorkshopPlan.Entry(size, detail?.updated ?: 0L, detail?.creator ?: 0L, detail?.title.orEmpty()))
                    states[k] = Item(id, Phase.INSTALLED, 1f, size, folder.path, detail?.title.orEmpty(), detail?.creator ?: 0L, detail?.updated ?: 0L)
                    appOf(packageName)?.let { events.note(it, "workshop item $id downloaded: ${size / 1024} KB, waits for the game to take it") }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "workshop item $id failed", e)
                    failures[k] = System.currentTimeMillis()
                    states[k] = Item(id, Phase.FAILED, error = e.message ?: e.javaClass.simpleName)
                    appOf(packageName)?.let { events.failure(it, "workshop item $id", e) }
                }
            }
        }
    }

    private fun sizeOf(folder: File): Long = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Steam's public record of an item: its title, its maker, the game it belongs to. Null when it cannot be read. */
    private fun lookup(id: Long): Details? {
        details[id]?.let { return it }
        return runCatching {
            val body = "itemcount=1&publishedfileids%5B0%5D=" + URLEncoder.encode(id.toString(), "UTF-8")
            val connection = (URL(DETAILS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val file = JSONObject(text).getJSONObject("response").getJSONArray("publishedfiledetails").getJSONObject(0)
            if (file.optInt("result") != 1) return@runCatching null
            Details(file.optString("title"), file.optString("creator").toLongOrNull() ?: 0L, file.optInt("consumer_app_id"), file.optLong("time_updated"), file.optString("hcontent_file").toLongOrNull() ?: 0L)
        }.getOrNull()?.also { details[id] = it }
    }

    private companion object {
        const val TAG = "GPWorkshop"
        const val DETAILS_URL = "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/"
        const val MAX_ITEMS = 200
        const val RETRY_AFTER_MS = 60_000L
        const val SUBSCRIPTIONS_EVERY_MS = 20_000L
    }
}
