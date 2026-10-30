// A stand-in for Meta's OVRPlugin, for a game whose build carries Meta's mixed reality scripts but not the native library they call. Without it the game raises
// an exception (DllNotFoundException) at every update, some forty times a second, and stutters. This answers "not initialized" to the one question those scripts
// ask, which is what they would hear from the real library on a device that does not run Meta's own runtime; the mixed reality features then stay off.
// It also tells the name of the device, which some games ask once at the start to choose their camera rig: without an answer the exception ends the script that
// was to set the rig up, and the game stays on a black screen. The name is the one the system gives (maker and model, as the engine prints it).
// It holds no code of Meta's and exports nothing else: the names and the functions are what the game looks up.
#include <stdio.h>
#include <sys/system_properties.h>

int ovrp_GetInitialized(void) { return 0; }

const char* ovrp_GetSystemProductName(void) {
    static char name[2 * PROP_VALUE_MAX + 2];
    char maker[PROP_VALUE_MAX] = "";
    char model[PROP_VALUE_MAX] = "";
    __system_property_get("ro.product.manufacturer", maker);
    __system_property_get("ro.product.model", model);
    snprintf(name, sizeof(name), "%s%s%s", maker, (maker[0] && model[0]) ? " " : "", model);
    return name;
}
