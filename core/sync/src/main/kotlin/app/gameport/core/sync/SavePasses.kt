package app.gameport.core.sync

/** One look at a place of Steam Cloud during a sync. */
enum class SavePass {
    /** The Android saves, Steam's way for Android builds: this device and them, both ways. */
    ANDROID,

    /** The Windows saves, read only: they feed this device, nothing is ever written to them. */
    PC_DOWNLOAD,

    /** The Windows saves, both ways: this device also writes to them. */
    PC_TWO_WAY,
    ;

    val isPc: Boolean get() = this != ANDROID
}

/** Which places of Steam Cloud a sync looks at, in order, from the mode the player chose. */
object SavePasses {
    /**
     * [launch] is the sync before the game reads its saves, as opposed to the ones while it is played or when it ends. A hook from before the
     * passes ([knowsPasses] false) only does one, so it always gets the Android saves.
     */
    fun of(mode: SaveSpaceMode, hasPc: Boolean, launch: Boolean, knowsPasses: Boolean): List<SavePass> = when {
        !hasPc || !knowsPasses || mode == SaveSpaceMode.ANDROID -> listOf(SavePass.ANDROID)
        // Windows feeds the device when the game starts; what the device holds goes to the Android saves, then and while it is played.
        mode == SaveSpaceMode.USE_PC -> if (launch) listOf(SavePass.PC_DOWNLOAD, SavePass.ANDROID) else listOf(SavePass.ANDROID)
        else -> listOf(SavePass.PC_TWO_WAY, SavePass.ANDROID)
    }
}

/** What GamePort remembers of how Steam describes a game's saves, to notice when Steam changes it. */
object SaveRulesSignature {
    /** The same rules give the same text whatever their order; any change of folder, pattern, depth, or Steam name gives another. */
    fun of(rules: List<app.gameport.core.model.SaveRule>): String =
        rules.map { listOf(it.localDir, it.pattern, if (it.recursive) "1" else "0", it.cloudPrefix, it.pcCloudPrefix.orEmpty()).joinToString("|") }.sorted().joinToString("\n")

    /**
     * The mode to keep when Steam's description of the saves changed. Using the PC saves only makes sense while the studio keeps them apart from the
     * Android ones: if Steam now links them (a root override), the saves are shared already and the choice has nothing left to do.
     */
    fun modeAfterChange(mode: SaveSpaceMode, stillApart: Boolean): SaveSpaceMode = if (stillApart) mode else SaveSpaceMode.ANDROID

    /**
     * Whether what GamePort agreed on with Steam for this game is no longer to be trusted, called when the rules now differ from the remembered ones. [before] is
     * the signature remembered (null when none was: a version from before it was kept, or a game never synced), [readDifferently] whether the scan of the library found
     * the game's rules read differently than before. Without a remembered signature, only a scan that says so is enough: a game seen for the first time has nothing to forget.
     * An old agreement kept under rules that were read wrongly could send a file that is only the game starting over, over the saves Steam holds.
     */
    fun mustForgetAgreement(before: String?, readDifferently: Boolean): Boolean = before != null || readDifferently
}
