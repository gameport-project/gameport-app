package app.gameport.core.steam.session

import app.gameport.core.model.LaunchOption
import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchOptionsReaderTest {
    private fun kv(name: String, value: String? = null, vararg children: KeyValue) =
        KeyValue(name, value).also { parent -> children.forEach { parent.children.add(it) } }

    /** The `launch` list Steam publishes for Quantum Void, trimmed to what matters. */
    private fun quantumVoid() = kv(
        "launch", null,
        kv("0", null, kv("executable", "Quantum Void.exe"), kv("type", "default"), kv("description", "Quantum Void normal mode")),
        kv("1", null, kv("executable", "Quantum Void.exe"), kv("arguments", "vrmode"), kv("type", "vr"), kv("description", "Quantum Void VR mode")),
        kv("2", null, kv("executable", "quantum_void_steamframe.apk"), kv("type", "openxr")),
    )

    @Test
    fun `only the entries that give arguments are listed, the VR one marked`() {
        assertEquals(listOf(LaunchOption("vrmode", "Quantum Void VR mode", vr = true)), LaunchOptionsReader.read(quantumVoid()))
    }

    @Test
    fun `the same arguments on several entries are listed once, VR if any of them is`() {
        val launch = kv(
            "launch", null,
            kv("0", null, kv("arguments", "-game x"), kv("type", "default")),
            kv("1", null, kv("arguments", " -game x "), kv("type", "vr"), kv("description", "Play in VR")),
            kv("2", null, kv("arguments", "-game y"), kv("description", "Other")),
        )
        assertEquals(
            listOf(LaunchOption("-game x", "Play in VR", vr = true), LaunchOption("-game y", "Other", vr = false)),
            LaunchOptionsReader.read(launch),
        )
    }

    @Test
    fun `a game with no launch list has no options`() {
        assertEquals(emptyList<LaunchOption>(), LaunchOptionsReader.read(KeyValue.INVALID))
    }

    @Test
    fun `an entry listed for the Android start is marked`() {
        val launch = kv(
            "launch", null,
            kv("0", null, kv("arguments", "es_vr"), kv("type", "openxr"), kv("config", null, kv("oslist", "windows"))),
            kv("1", null, kv("arguments", "es_vr"), kv("type", "openxr"), kv("config", null, kv("oslist", "android"))),
            kv("2", null, kv("arguments", "-d3d11"), kv("type", "openxr"), kv("config", null, kv("oslist", "windows"))),
        )
        assertEquals(
            listOf(LaunchOption("es_vr", null, vr = true, android = true), LaunchOption("-d3d11", null, vr = true, android = false)),
            LaunchOptionsReader.read(launch),
        )
    }

    @Test
    fun `an entry that mixes a switch for VR with other arguments is offered flag by flag`() {
        val launch = kv("launch", null, kv("0", null, kv("arguments", "-vr -dx11"), kv("type", "openxr"), kv("description", "Sushi Ben Demo")))
        assertEquals(
            listOf(LaunchOption("-vr", "Sushi Ben Demo", vr = true), LaunchOption("-dx11", "Sushi Ben Demo", vr = true)),
            LaunchOptionsReader.read(launch),
        )
    }
}
