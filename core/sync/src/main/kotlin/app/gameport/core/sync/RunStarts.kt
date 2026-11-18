package app.gameport.core.sync

/** Whether a game that says it started is a new run: the cloud sync of a running game says so again every half minute, and those are not launches. */
internal object RunStarts {
    /**
     * [storedPid] is the process of the launch kept, [pid] the one that says it now (0 when the hook does not tell), [recorded] whether a launch is kept. A process that is not the one
     * kept, or any that says it with none kept, begins a run; the same process again does not.
     */
    fun isNew(storedPid: Int, pid: Int, recorded: Boolean): Boolean = pid == 0 || !recorded || storedPid != pid
}
