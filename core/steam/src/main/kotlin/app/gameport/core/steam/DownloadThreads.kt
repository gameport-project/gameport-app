package app.gameport.core.steam

import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * A download keeps a dozen worker threads busy (decrypting, unpacking and writing about 150 MB of buffers a second on a fast link), at the same priority as the screen. The
 * app then lagged while a game downloaded. A download is not urgent: while one goes on, the shared worker threads of the app run at background priority, the way Android
 * asks downloads to, and go back to normal when the last one ends. The thread that draws the screen is never touched.
 */
internal object DownloadThreads {
    private val active = AtomicInteger(0)

    fun begin() {
        active.incrementAndGet()
        apply(background = true)
    }

    /** Run again from time to time while a download goes on: the pool starts new threads as it needs them. */
    fun refresh() {
        if (active.get() > 0) apply(background = true)
    }

    fun end() {
        if (active.decrementAndGet() <= 0) {
            active.set(0)
            apply(background = false)
        }
    }

    private fun apply(background: Boolean) {
        val priority = if (background) Process.THREAD_PRIORITY_BACKGROUND else Process.THREAD_PRIORITY_DEFAULT
        val tasks = File("/proc/self/task").listFiles() ?: return
        for (task in tasks) {
            val tid = task.name.toIntOrNull() ?: continue
            // The name is cut at 15 characters: the coroutine pool's threads are "DefaultDispatch…", the HTTP client's "OkHttp …".
            val name = runCatching { File(task, "comm").readText().trim() }.getOrDefault("")
            if (name.startsWith("DefaultDispatch") || name.startsWith("OkHttp")) runCatching { Process.setThreadPriority(tid, priority) }
        }
    }
}
