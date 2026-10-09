package app.gameport.core.sync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the code of GamePort asks of Android must be asked in its manifest: a missing permission is not seen by the compiler and crashes at the moment of use. */
class ManifestPermissionsTest {
    private val manifest = File("../../app/src/main/AndroidManifest.xml").readText()

    private fun declares(permission: String) = manifest.contains("<uses-permission android:name=\"android.permission.$permission\"")

    @Test
    fun `the network is reachable and its state can be read`() {
        assertTrue("INTERNET", declares("INTERNET"))
        // VerdictAsker reads the state of the network when a game ends: without this permission it fails with a SecurityException.
        assertTrue("ACCESS_NETWORK_STATE", declares("ACCESS_NETWORK_STATE"))
    }

    @Test
    fun `every use of the network state in the sources comes with its permission`() {
        val users = File("../..").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path && "/build/" !in it.path }
            .filter { it.readText().contains("ConnectivityManager") }
            .map { it.name }
            .toList()
        if (users.isNotEmpty()) assertTrue("these files read the state of the network: $users", declares("ACCESS_NETWORK_STATE"))
    }
}
