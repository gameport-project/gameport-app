package app.gameport.core.steam.session

import app.gameport.core.model.LaunchOption
import app.gameport.core.model.LaunchOptions
import `in`.dragonbra.javasteam.types.KeyValue

/**
 * Reads the `launch` list of an app's `config` section into the arguments each way of starting the game gives it. Entries without arguments say
 * nothing to pass and are left out; entries of any system are kept, since a game's Android start often has none of its own and the VR argument is
 * published on the entry for another system.
 */
internal object LaunchOptionsReader {
    fun read(launch: KeyValue): List<LaunchOption> {
        val found = LinkedHashMap<String, LaunchOption>()
        launch.children.forEach { entry ->
            val arguments = entry["arguments"].value?.trim().orEmpty()
            if (arguments.isEmpty()) return@forEach
            val description = entry["description"].value?.trim()?.takeIf { it.isNotEmpty() }
            val android = entry["config"]["oslist"].value.orEmpty().split(',').any { it.trim() == "android" }
            // Each flag is offered on its own: an entry such as `-vr -dx11` mixes a switch for VR with an argument for the Windows start.
            LaunchOptions.flags(arguments).forEach { flag ->
                val vr = LaunchOptions.isVr(entry["type"].value, description, flag)
                val before = found[flag]
                found[flag] = LaunchOption(flag, before?.description ?: description, vr = vr || before?.vr == true, android = android || before?.android == true)
            }
        }
        return found.values.toList()
    }
}
