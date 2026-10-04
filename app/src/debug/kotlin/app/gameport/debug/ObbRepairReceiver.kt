package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.system.Os
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * adb shell am broadcast -n app.gameport/app.gameport.debug.ObbRepairReceiver --es pkg <package> [--es file <path under Android/obb/<package>>]
 * Test only. Makes a fresh copy of the expansion files of a game, by GamePort itself, and says whose each file is before and after (tag
 * GPObb): a file moved from GamePort's own folder keeps GamePort as its owner, and the game may not be able to open it. Without `file`,
 * every file under the game's obb folder whose owner is not the game is copied.
 */
@AndroidEntryPoint
class ObbRepairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("pkg") ?: return
        val only = intent.getStringExtra("file")
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val gameUid = context.packageManager.getApplicationInfo(pkg, 0).uid
                if (intent.getBooleanExtra("chmod", false)) {
                    // Test: let every app read the files (what an adb push gives), and say what the mode is afterwards.
                    val targets = if (only != null) listOf(File("/storage/emulated/0/Android/obb/$pkg", only)) else File("/storage/emulated/0/Android/obb/$pkg").walkTopDown().filter { it.isFile }.toList()
                    var changed = 0
                    for (file in targets) {
                        val mode = Os.stat(file.path).st_mode and 0xFFF
                        if (mode and 4 != 0) continue
                        // The file of a game that is not GamePort's (the .obb) is not for GamePort to change.
                        if (runCatching { Os.chmod(file.path, mode or 4) }.isSuccess) changed++
                    }
                    Log.i(TAG, "chmod: ${targets.size} file(s) looked at, $changed made readable by all; ")
                    return@runCatching
                }
                if (intent.getBooleanExtra("probe", false)) {
                    // Whose is a file that GamePort creates at the top of the game's obb folder, and one in a folder below it?
                    val top = File("/storage/emulated/0/Android/obb/$pkg/gp_probe_top.tmp")
                    val sub = File("/storage/emulated/0/Android/obb/$pkg/gp_probe_dir/gp_probe_sub.tmp")
                    sub.parentFile?.mkdirs()
                    top.writeText("x")
                    sub.writeText("x")
                    Log.i(TAG, "probe: top-level file owner ${Os.stat(top.path).st_uid}, file below ${Os.stat(sub.path).st_uid}, folder below ${Os.stat(sub.parent!!).st_uid} (game $gameUid)")
                    top.delete()
                    sub.delete()
                    sub.parentFile?.delete()
                    return@runCatching
                }
                val root = File("/storage/emulated/0/Android/obb/$pkg")
                val files = if (only != null) listOf(File(root, only)) else root.walkTopDown().filter { it.isFile }.toList()
                var fixed = 0
                for (file in files) {
                    val before = Os.stat(file.path).st_uid
                    if (before == gameUid) continue
                    val copy = File(file.parentFile, file.name + ".gpcopy")
                    file.copyTo(copy, overwrite = true)
                    val after = Os.stat(copy.path).st_uid
                    Log.i(TAG, "${file.name}: owner $before -> copy $after (game $gameUid)")
                    if (after == gameUid) {
                        file.delete()
                        copy.renameTo(file)
                        fixed++
                    } else copy.delete()
                }
                Log.i(TAG, "done: $fixed of ${files.size} file(s) now owned by the game")
            }.onFailure { Log.w(TAG, "failed", it) }
        }
    }

    private companion object {
        const val TAG = "GPObb"
    }
}
