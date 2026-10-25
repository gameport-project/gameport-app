// A stand-in for Meta's OVRPlugin, for a game whose build carries Meta's mixed reality scripts but not the native library they call. Without it the game raises
// an exception (DllNotFoundException) at every update, some forty times a second, and stutters. This answers "not initialized" to the one question those scripts
// ask, which is what they would hear from the real library on a device that does not run Meta's own runtime; the mixed reality features then stay off.
// It holds no code of Meta's and exports nothing else: the name and the function are what the game looks up.
int ovrp_GetInitialized(void) { return 0; }
