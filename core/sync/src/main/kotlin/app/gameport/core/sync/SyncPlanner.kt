package app.gameport.core.sync

/**
 * Decides what a sync has to do. It follows Steam's own auto-cloud logic: each side is compared
 * with what both agreed on last time ([Baseline]); if only one moved, it wins; if both moved and
 * they differ, that is a conflict and nothing is overwritten until the player chooses.
 */
object SyncPlanner {
    fun plan(
        local: Map<String, LocalFile>,
        cloud: Map<String, CloudFile>,
        cloudChangeNumber: Long,
        baseline: Baseline?,
        force: Side? = null,
    ): SyncAction {
        when (force) {
            Side.CLOUD -> return download(local, cloud)
            Side.LOCAL -> return upload(local, cloud)
            null -> Unit
        }
        if (identical(local, cloud)) return SyncAction.None

        // Everything vanished from the phone since the last sync while the cloud still has saves:
        // that is a reinstall or cleared data, not the player deleting every save. Restore, and
        // never let it turn into deleting the cloud copy.
        if (baseline != null && local.isEmpty() && cloud.isNotEmpty()) return download(local, cloud)

        val localChanged = if (baseline == null) local.isNotEmpty() else local.mapValues { it.value.sha1 } != baseline.files
        // Judged by content, not by Steam's change number: the number also moves for files outside
        // the game's save rules and for our own housekeeping, which would look like conflicts.
        val cloudChanged = if (baseline == null) cloud.isNotEmpty() else cloud.mapValues { it.value.sha1 } != baseline.files

        return when {
            !localChanged && !cloudChanged -> SyncAction.None
            cloudChanged && !localChanged -> download(local, cloud)
            localChanged && !cloudChanged -> upload(local, cloud)
            // Both moved. An empty side has nothing to lose, so the other one simply wins.
            local.isEmpty() -> download(local, cloud)
            cloud.isEmpty() -> upload(local, cloud)
            else -> SyncAction.Conflict(
                local = SideSummary(local.size, local.values.sumOf { it.size }, local.values.maxOf { it.mtime }),
                cloud = SideSummary(cloud.size, cloud.values.sumOf { it.size }, cloud.values.maxOf { it.timestamp }),
            )
        }
    }

    private fun identical(local: Map<String, LocalFile>, cloud: Map<String, CloudFile>) =
        local.keys == cloud.keys && local.all { (rel, file) -> cloud.getValue(rel).sha1 == file.sha1 }

    private fun download(local: Map<String, LocalFile>, cloud: Map<String, CloudFile>) = SyncAction.Download(
        files = cloud.values.filter { local[it.rel]?.sha1 != it.sha1 },
        deleteLocal = local.keys.filter { it !in cloud },
    )

    private fun upload(local: Map<String, LocalFile>, cloud: Map<String, CloudFile>) = SyncAction.Upload(
        files = local.values.filter { cloud[it.rel]?.sha1 != it.sha1 },
        deleteCloud = cloud.values.filter { it.rel !in local },
    )
}
