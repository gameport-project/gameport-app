package app.gameport.core.model

/**
 * Tells when an installed game may be missing its expansion files, which GamePort cannot look for: Android hides other apps' `Android/obb` folders. What it can compare
 * is what Steam says the game weighs once installed with what the install itself holds. A game that weighs far more on Steam than its APK has something besides the APK; if
 * nothing was noted about it at the install, whether that something is on the device is not known.
 */
object ExpansionCheck {
    /** Under this, what the depot holds besides the APK is taken for small files, not an expansion. */
    const val MIN_EXTRA_BYTES = 300L * 1024 * 1024

    /**
     * [depotInstallBytes] is Steam's size of the game once installed (null when Steam does not say), [apkBytes] what its APKs weigh on the device, and
     * [noted] what was written down about its expansion files at the install: null for a game installed before that was done, zero when there were none.
     * A game whose expansion was noted as present is never suspected.
     */
    fun mayBeMissing(depotInstallBytes: Long?, apkBytes: Long, noted: Long?): Boolean {
        if (noted != null && noted > 0) return false
        val depot = depotInstallBytes ?: return false
        return depot - apkBytes >= MIN_EXTRA_BYTES
    }
}
