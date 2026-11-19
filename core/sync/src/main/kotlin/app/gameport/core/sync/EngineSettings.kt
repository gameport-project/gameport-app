package app.gameport.core.sync

/**
 * What a report says of the game's engine settings, read from the game's APK so it can be compared with other games without the game itself: the version of Unity and the
 * text of its `boot.config` (the settings the player reads before anything else, among them the graphics threading and the XR options). Nothing in it is about the player.
 */
internal object EngineSettings {
    const val BOOT_CONFIG = "assets/bin/Data/boot.config"
    const val DATA_FILE = "assets/bin/Data/globalgamemanagers"

    /** How much of the start of the data file is searched for the version, and the most of the boot.config that is kept. */
    const val HEAD_BYTES = 600
    const val MAX_CONFIG_BYTES = 16 * 1024
    private const val MAX_LINES = 80
    private const val MAX_LINE = 160

    private val VERSION = Regex("(?<![\\d.])(\\d{4})\\.(\\d{1,2})\\.(\\d{1,3})[abfp](\\d{1,3})(?![\\d.])")

    /** The version of Unity written at the start of its data file (`6000.3.12f1`), or null when none is there. */
    fun unityVersion(head: ByteArray): String? = VERSION.find(String(head, 0, minOf(head.size, HEAD_BYTES), Charsets.ISO_8859_1))?.value

    /** The settings of a boot.config as `name=value` lines: blank lines and anything that is not a setting left out, the number and the length of the lines bounded. */
    fun bootConfig(text: String): List<String> = text.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() && it.contains('=') && !it.startsWith("#") }
        .map { it.take(MAX_LINE) }
        .take(MAX_LINES)
        .toList()

    /** The report's text: the engine, its version, and the settings. [engine] says what the APK holds (`Unity`, `Unreal`, or null). */
    fun text(engine: String?, version: String?, settings: List<String>): String = buildString {
        when {
            engine == null -> appendLine("engine: not recognised (no Unity data, no Unreal library in the APK)")
            version != null -> appendLine("engine: $engine $version")
            else -> appendLine("engine: $engine (version not found)")
        }
        if (settings.isEmpty()) {
            appendLine("boot.config: none")
        } else {
            appendLine("boot.config:")
            settings.forEach { appendLine("  $it") }
        }
    }
}
