package app.gameport.core.install

/**
 * The room an install needs on the device.
 *
 * The files of a game take their size once written, which is the figure Steam gives for its depots, not twice that: patching only writes
 * a second copy of the APK, and the data under `obb/` is moved, not copied. The copy is checked on its own once the download is there and
 * the APK's real size is known.
 */
internal object SpaceNeeds {
    /** What is kept free besides the files themselves: temporary files, the installer, the games' own first start. */
    const val MARGIN_BYTES = 256L * 1024 * 1024

    /** Before the download: the files of the depots, less what a resumed download already holds. */
    fun toInstall(installBytes: Long, alreadyThere: Long): Long = installBytes + MARGIN_BYTES - alreadyThere

    /** Before patching: a patched copy of each APK is written next to the original, which is deleted once it is done. */
    fun toPatch(apkBytes: Long): Long = apkBytes + MARGIN_BYTES
}
