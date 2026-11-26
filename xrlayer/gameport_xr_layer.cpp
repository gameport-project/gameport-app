// GamePort OpenXR API layer.
//
// Loaded by the OpenXR loader inside a patched game. It does two things:
//  * logs what the game asks of the runtime where that helps to understand compatibility
//    problems (swapchain creation, interaction profile bindings), tag "GPXR";
//  * when the player sits, raises the game's world so it sees them at their standing eye height.
//
// Seated mode: the game measures the player against the floor ("stage" space). Sitting, the head
// is too low. The layer measures the real head height once per session, computes the gap to the
// standing eye height the player configured, and adds that gap to every pose the game reads in a
// floor-based space. It takes the same gap off the poses the game submits for display, so what is
// shown still lines up with where the head really is: the virtual floor simply sits lower.
//
// Configuration (set by GamePort's hook before the game starts):
//   GAMEPORT_XR_SEATED=1          turn seated mode on
//   GAMEPORT_XR_EYE_CM=<number>   standing eye height, in centimetres

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <mutex>
#include <set>
#include <string>
#include <unordered_map>
#include <vector>

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_VULKAN
#include <vulkan/vulkan.h>
#include <openxr/openxr.h>
#include <openxr/openxr_loader_negotiation.h>
#include <openxr/openxr_platform.h>

#include "controller_allocation.h"
#include "haptic_scale.h"

#define TAG "GPXR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

constexpr float kMaxOffsetMeters = 1.0f;
constexpr int kCalibrationSamples = 90;
constexpr float kAlreadyStandingMeters = 0.10f;

PFN_xrGetInstanceProcAddr g_nextGetInstanceProcAddr = nullptr;
XrInstance g_instance = XR_NULL_HANDLE;
XrSession g_session = XR_NULL_HANDLE;

PFN_xrCreateReferenceSpace g_createReferenceSpace = nullptr;
PFN_xrDestroySpace g_destroySpace = nullptr;
PFN_xrLocateSpace g_locateSpace = nullptr;
PFN_xrLocateSpaces g_locateSpaces = nullptr;
PFN_xrLocateViews g_locateViews = nullptr;
PFN_xrEndFrame g_endFrame = nullptr;
PFN_xrEnumerateEnvironmentBlendModes g_enumerateBlendModes = nullptr;
// The player chose passthrough for this game: the alpha blend mode is offered to it when the runtime does not (see Layer_xrEnumerateEnvironmentBlendModes).
std::atomic<bool> g_offerAlphaBlend{false};
std::atomic<bool> g_alphaOffered{false};   // the runtime did not offer it and the layer did
std::atomic<bool> g_alphaByLayerBit{false};  // Pico offers the alpha blend mode itself, but blends only the layers that say so: the player chose passthrough
std::atomic<bool> g_alphaRefused{false};   // the runtime refused a frame in that mode: frames go out opaque from then on

// The headset refuses the alpha blend mode for some apps (the runtime answers -42 to a frame in that mode, whatever the manifest says), but it shows the real world to an app
// that submits Meta's passthrough layer under its own. A game that asks for the alpha blend mode is given that layer: the frame goes out opaque, with the passthrough layer
// first and the game's layers blending over it by their own alpha.
constexpr const char* kFbPassthrough = "XR_FB_passthrough";
PFN_xrCreatePassthroughFB g_createPassthrough = nullptr;
PFN_xrCreatePassthroughLayerFB g_createPassthroughLayer = nullptr;
XrPassthroughFB g_passthrough = XR_NULL_HANDLE;
XrPassthroughLayerFB g_passthroughLayer = XR_NULL_HANDLE;
std::atomic<bool> g_passthroughAdded{false};   // the extension is in the instance (the layer added it, or the game asked for it)
std::atomic<int> g_passthroughState{0};        // 0 not tried, 1 ready, -1 could not be made

bool EnsurePassthrough(XrSession session) {
    if (g_passthroughState.load() != 0) return g_passthroughState.load() == 1;
    if (!g_passthroughAdded.load() || !g_nextGetInstanceProcAddr || g_instance == XR_NULL_HANDLE) { g_passthroughState.store(-1); return false; }
    PFN_xrVoidFunction create = nullptr, layer = nullptr;
    g_nextGetInstanceProcAddr(g_instance, "xrCreatePassthroughFB", &create);
    g_nextGetInstanceProcAddr(g_instance, "xrCreatePassthroughLayerFB", &layer);
    g_createPassthrough = reinterpret_cast<PFN_xrCreatePassthroughFB>(create);
    g_createPassthroughLayer = reinterpret_cast<PFN_xrCreatePassthroughLayerFB>(layer);
    if (!g_createPassthrough || !g_createPassthroughLayer) { LOGI("passthrough: the runtime has no function to make a passthrough layer"); g_passthroughState.store(-1); return false; }
    XrPassthroughCreateInfoFB passthroughInfo{XR_TYPE_PASSTHROUGH_CREATE_INFO_FB};
    passthroughInfo.flags = XR_PASSTHROUGH_IS_RUNNING_AT_CREATION_BIT_FB;
    XrResult result = g_createPassthrough(session, &passthroughInfo, &g_passthrough);
    if (XR_FAILED(result)) { LOGI("passthrough: xrCreatePassthroughFB -> %d", (int)result); g_passthroughState.store(-1); return false; }
    XrPassthroughLayerCreateInfoFB layerInfo{XR_TYPE_PASSTHROUGH_LAYER_CREATE_INFO_FB};
    layerInfo.passthrough = g_passthrough;
    layerInfo.flags = XR_PASSTHROUGH_IS_RUNNING_AT_CREATION_BIT_FB;
    layerInfo.purpose = XR_PASSTHROUGH_LAYER_PURPOSE_RECONSTRUCTION_FB;
    result = g_createPassthroughLayer(session, &layerInfo, &g_passthroughLayer);
    if (XR_FAILED(result)) { LOGI("passthrough: xrCreatePassthroughLayerFB -> %d", (int)result); g_passthroughState.store(-1); return false; }
    LOGI("passthrough: a passthrough layer is made for the game (it asks for the alpha blend mode)");
    g_passthroughState.store(1);
    return true;
}
PFN_xrWaitFrame g_waitFrame = nullptr;
PFN_xrBeginSession g_beginSession = nullptr;
PFN_xrCreateSwapchain g_createSwapchain = nullptr;
PFN_xrSuggestInteractionProfileBindings g_suggestBindings = nullptr;
PFN_xrApplyHapticFeedback g_applyHaptic = nullptr;
// How strongly the controllers vibrate, as a percentage of what the game asks for (GAMEPORT_XR_HAPTICS, the player's choice): 100 leaves every call as it is.
std::atomic<int> g_hapticPercent{100};
PFN_xrDestroySwapchain g_destroySwapchain = nullptr;
PFN_xrStringToPath g_stringToPath = nullptr;
PFN_xrCreateAction g_createAction = nullptr;
PFN_xrEndSession g_endSession = nullptr;
PFN_xrRequestExitSession g_requestExit = nullptr;
PFN_xrDestroySession g_destroySession = nullptr;
PFN_xrDestroyInstance g_destroyInstance = nullptr;
std::mutex g_actionNamesMutex;
std::unordered_map<uint64_t, std::string> g_actionNames;  // the game's own name for each action, so the log can say what a control is for
PFN_xrPathToString g_pathToString = nullptr;
PFN_xrGetCurrentInteractionProfile g_getCurrentProfile = nullptr;

PFN_xrAttachSessionActionSets g_attachActionSets = nullptr;
PFN_xrPollEvent g_pollEvent = nullptr;
PFN_xrGetReferenceSpaceBoundsRect g_getBounds = nullptr;
PFN_xrGetActionStateBoolean g_getActionBoolean = nullptr;
PFN_xrGetActionStateFloat g_getActionFloat = nullptr;
// For the button recorder (see RecorderOn): the functions it needs beyond the ones the layer already holds.
PFN_xrCreateActionSet g_createActionSet = nullptr;
PFN_xrGetActionStateVector2f g_getActionVector2 = nullptr;
PFN_xrSyncActions g_syncActions = nullptr;
PFN_xrGetInstanceProperties g_getInstanceProperties = nullptr;
PFN_xrGetSystemProperties g_getSystemProperties = nullptr;
PFN_xrCreateVulkanDeviceKHR g_createVulkanDevice = nullptr;
PFN_xrPerfSettingsSetPerformanceLevelEXT g_perfLevel = nullptr;
PFN_xrRequestDisplayRefreshRateFB g_refreshRequest = nullptr;
PFN_xrEnumerateDisplayRefreshRatesFB g_refreshEnumerate = nullptr;
// What the layer knows of the frame rate, for the file the app reads (see WriteRefreshReport): the rate GamePort asked for and the runtime's answer, the one the game asked for itself,
// and when the session began.
std::atomic<int> g_refreshAsked{0}, g_refreshResult{0}, g_refreshGameAsked{0}, g_refreshStage{0};
std::atomic<long long> g_sessionStartMs{0};
std::string g_refreshAccepted;  // the rates the runtime accepts, as "60,72,90" (guarded by g_mutex)
PFN_xrEnumerateViewConfigurationViews g_enumerateViews = nullptr;
typedef XrResult (XRAPI_PTR *PFN_xrEnumerateSwapchainFormats)(XrSession, uint32_t, uint32_t*, int64_t*);
PFN_xrEnumerateSwapchainFormats g_enumerateSwapchainFormats = nullptr;

struct Size { uint32_t width, height; };
std::unordered_map<XrSwapchain, Size> g_swapchainSize;  // what each swapchain really is (guarded by g_mutex)
std::atomic<int> g_endFrameFailures{0};
// Once the runtime is seen to refuse the game's layers: 0 = submit as they are, 1 = without the
// placeholder quads, 2 = without any quad (the projection layers only).
std::atomic<int> g_dropMode{0};
// What the compositor is given, counted for the check of the frames (see FrameCheck): frames ended, those without any layer, those the runtime said not to render, and where the head is
// in the space the game located its views in.
std::atomic<uint32_t> g_frameCount{0}, g_emptyFrames{0}, g_noRenderFrames{0};
// A Pico's runtime says nothing the game's log can use of how steadily the game runs: on a Pico the layer counts the frames itself, once a second, and says them in the form
// of Meta's line ("FPS=<frames shown>/<frames asked for>", which the hook reads); the frames asked for are those of the display period the runtime announced at the last wait.
std::atomic<bool> g_countRate{false};
std::atomic<int> g_displayRate{0};
std::atomic<uint32_t> g_rateFrames{0};
std::atomic<float> g_headX{0.0f}, g_headY{0.0f}, g_headZ{0.0f};
std::atomic<int> g_headValid{-1}, g_headFloor{-1};

std::mutex g_mutex;
std::unordered_map<XrSpace, bool> g_floorBased;  // reference space handle -> uses the floor as origin

bool g_seated = false;
float g_targetEyeMeters = 0.0f;
std::atomic<float> g_offset{0.0f};

// Calibration: our own floor and view spaces, sampled for the first frames of a session.
XrSpace g_ownStage = XR_NULL_HANDLE;
XrSpace g_ownView = XR_NULL_HANDLE;
bool g_calibrating = false;
int g_samples = 0;
double g_sum = 0.0;

bool IsFloorBased(XrReferenceSpaceType type) {
    return type == XR_REFERENCE_SPACE_TYPE_STAGE || type == XR_REFERENCE_SPACE_TYPE_LOCAL_FLOOR;
}

bool FloorBased(XrSpace space) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_floorBased.find(space);
    return it != g_floorBased.end() && it->second;
}

// A space that is not a reference space at all (a view, an action space) is not floor based.
void AddOffset(XrPosef& pose, float sign) { pose.position.y += sign * g_offset.load(); }

XrPosef IdentityPose() {
    XrPosef pose{};
    pose.orientation.w = 1.0f;
    return pose;
}

// --- intercepted functions -------------------------------------------------------------

XrResult XRAPI_CALL Layer_xrCreateSession(XrInstance, const XrSessionCreateInfo*, XrSession*);

std::string GamePortFolder();
bool RecorderOn();
// Whether a game that asks for a stage gets the play space the headset's recentering moves: 0 where the headset has no play area, 1 always, 2 never (GAMEPORT_XR_LOCAL_FLOOR).
std::atomic<int> g_recenterMode{0};

XrResult XRAPI_CALL Layer_xrCreateReferenceSpace(XrSession session, const XrReferenceSpaceCreateInfo* info, XrSpace* space) {
    // A stage is fixed to the play area, which the headset's recentering does not move: a game that places its content there stays where it was. The local floor
    // has the floor at the same height and follows the recentering. When the runtime has no local floor, the game gets the stage it asked for.
    // The player can ask for it for a game; and it is done by itself on a headset whose stage is only the fallback of a missing play area.
    if (info && space && info->referenceSpaceType == XR_REFERENCE_SPACE_TYPE_STAGE) {
        const int mode = g_recenterMode.load();
        const bool asked = mode == 1;
        bool fallback = false;
        if (mode == 0 && g_getBounds) {
            XrExtent2Df bounds{};
            const XrResult read = g_getBounds(session, XR_REFERENCE_SPACE_TYPE_STAGE, &bounds);
            fallback = XR_SUCCEEDED(read) && gp::StageLooksLikeFallback(read == XR_SUCCESS, bounds.width, bounds.height);
            static std::atomic<int> measured{0};
            if (measured.fetch_add(1) < 2) LOGI("the stage of this headset: bounds %s, %.2f x %.2f m%s", read == XR_SUCCESS ? "given" : "unavailable", bounds.width, bounds.height, fallback ? ": no play area" : "");
        }
        if (asked || fallback) {
            XrReferenceSpaceCreateInfo floor = *info;
            floor.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL_FLOOR;
            const XrResult swapped = g_createReferenceSpace(session, &floor, space);
            if (XR_SUCCEEDED(swapped)) {
                {
                    std::lock_guard<std::mutex> lock(g_mutex);
                    g_floorBased[*space] = true;
                }
                static std::atomic<int> logged{0};
                if (logged.fetch_add(1) < 4) LOGI("the game asked for a stage space: given a local floor, which follows the recentering (%s)", asked ? "as the player asked" : "the headset has no play area");
                // GamePort is told the layer did it by itself, to say so on the game's page.
                if (!asked) {
                    FILE* mark = fopen((GamePortFolder() + "xr_stage_fallback.txt").c_str(), "w");
                    if (mark) {
                        fputs("1\n", mark);
                        fclose(mark);
                    }
                }
                return swapped;
            }
        }
    }
    XrResult result = g_createReferenceSpace(session, info, space);
    if (XR_SUCCEEDED(result) && info && space) {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_floorBased[*space] = IsFloorBased(info->referenceSpaceType);
        LOGI("reference space type %d created (floor based: %d), pose in it %.2f %.2f %.2f, turned %.2f %.2f %.2f %.2f", (int)info->referenceSpaceType, (int)IsFloorBased(info->referenceSpaceType),
             info->poseInReferenceSpace.position.x, info->poseInReferenceSpace.position.y, info->poseInReferenceSpace.position.z, info->poseInReferenceSpace.orientation.x,
             info->poseInReferenceSpace.orientation.y, info->poseInReferenceSpace.orientation.z, info->poseInReferenceSpace.orientation.w);
    }
    return result;
}

XrResult XRAPI_CALL Layer_xrDestroySpace(XrSpace space) {
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_floorBased.erase(space);
    }
    return g_destroySpace(space);
}

XrResult XRAPI_CALL Layer_xrLocateSpace(XrSpace space, XrSpace baseSpace, XrTime time, XrSpaceLocation* location) {
    XrResult result = g_locateSpace(space, baseSpace, time, location);
    if (g_offset.load() != 0.0f && XR_SUCCEEDED(result) && location && (location->locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT)) {
        bool spaceFloor = FloorBased(space), baseFloor = FloorBased(baseSpace);
        if (baseFloor && !spaceFloor) AddOffset(location->pose, +1.0f);
        else if (spaceFloor && !baseFloor) AddOffset(location->pose, -1.0f);
    }
    return result;
}

XrResult XRAPI_CALL Layer_xrLocateSpaces(XrSession session, const XrSpacesLocateInfo* info, XrSpaceLocations* locations) {
    XrResult result = g_locateSpaces(session, info, locations);
    if (g_offset.load() != 0.0f && XR_SUCCEEDED(result) && info && locations && locations->locations && FloorBased(info->baseSpace)) {
        for (uint32_t i = 0; i < locations->locationCount && i < info->spaceCount; i++) {
            XrSpaceLocationData& data = locations->locations[i];
            if ((data.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) && !FloorBased(info->spaces[i])) AddOffset(data.pose, +1.0f);
        }
    }
    return result;
}

XrResult XRAPI_CALL Layer_xrLocateViews(XrSession session, const XrViewLocateInfo* info, XrViewState* state, uint32_t capacity, uint32_t* countOut, XrView* views) {
    XrResult result = g_locateViews(session, info, state, capacity, countOut, views);
    if (g_offset.load() != 0.0f && XR_SUCCEEDED(result) && info && views && countOut && FloorBased(info->space)) {
        for (uint32_t i = 0; i < *countOut; i++) AddOffset(views[i].pose, +1.0f);
    }
    // Only a location in a floor based space is kept: in the view space the eyes are a few centimetres from the origin, whatever the head does.
    if (XR_SUCCEEDED(result) && info && state && views && countOut && *countOut > 0 && FloorBased(info->space)) {
        g_headX.store(views[0].pose.position.x);
        g_headY.store(views[0].pose.position.y);
        g_headZ.store(views[0].pose.position.z);
        g_headValid.store((state->viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT) ? 1 : 0);
        g_headFloor.store(1);
    }
    return result;
}

// A game that was told a wrong swapchain size may submit an empty image rectangle; the swapchain's
// real size is the only sensible one.
bool FixRect(XrSwapchain swapchain, XrSwapchainSubImage& sub) {
    if (sub.imageRect.extent.width > 0 && sub.imageRect.extent.height > 0) return false;
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_swapchainSize.find(swapchain);
    if (it == g_swapchainSize.end()) return false;
    sub.imageRect.offset = {0, 0};
    sub.imageRect.extent = {(int32_t)it->second.width, (int32_t)it->second.height};
    return true;
}

// The structures chained to another (depth, color scale and bias...), by number, with their values where they matter: a color scale of zero shows nothing, whatever the game draws.
std::string ChainTypes(const void* next) {
    std::string out;
    int count = 0;
    for (const XrBaseInStructure* link = reinterpret_cast<const XrBaseInStructure*>(next); link && count < 8; link = link->next, count++) {
        char detail[160] = "";
        if (link->type == XR_TYPE_COMPOSITION_LAYER_COLOR_SCALE_BIAS_KHR) {
            const auto* color = reinterpret_cast<const XrCompositionLayerColorScaleBiasKHR*>(link);
            snprintf(detail, sizeof(detail), " (color scale %.2f %.2f %.2f %.2f, bias %.2f %.2f %.2f %.2f)", color->colorScale.r, color->colorScale.g, color->colorScale.b, color->colorScale.a,
                     color->colorBias.r, color->colorBias.g, color->colorBias.b, color->colorBias.a);
        } else if (link->type == XR_TYPE_COMPOSITION_LAYER_DEPTH_TEST_FB) {
            const auto* test = reinterpret_cast<const XrCompositionLayerDepthTestFB*>(link);
            snprintf(detail, sizeof(detail), " (depth test: mask %u, compare %d)", (unsigned)test->depthMask, (int)test->compareOp);
        } else if (link->type == XR_TYPE_COMPOSITION_LAYER_DEPTH_INFO_KHR) {
            const auto* depth = reinterpret_cast<const XrCompositionLayerDepthInfoKHR*>(link);
            snprintf(detail, sizeof(detail), " (depth range %.2f to %.2f, near %.2f, far %.2f)", depth->minDepth, depth->maxDepth, depth->nearZ, depth->farZ);
        }
        out += " " + std::to_string((int)link->type) + detail;
    }
    return out.empty() ? " none" : out;
}

// What the game gives the compositor, said every two seconds for the first forty seconds of a session and then every half minute: how many frames, how many ended without a layer
// or were told not to render, the layers of the last one, and where the head is. It tells a black screen from a game that draws nothing apart from one the headset does not show.
void FrameCheck(const XrFrameEndInfo* info) {
    using Clock = std::chrono::steady_clock;
    static const Clock::time_point began = Clock::now();
    static std::atomic<int64_t> lastSaid{-1000000};
    g_frameCount.fetch_add(1);
    if (!info || info->layerCount == 0) g_emptyFrames.fetch_add(1);
    const int64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - began).count();
    if (g_countRate.load()) {
        static std::atomic<int64_t> rateSince{-1};
        g_rateFrames.fetch_add(1);
        int64_t since = rateSince.load();
        if (since < 0) rateSince.compare_exchange_strong(since, now);
        else if (now - since >= 1000 && rateSince.compare_exchange_strong(since, now)) {
            const uint32_t frames = g_rateFrames.exchange(0);
            const int shown = (int)((frames * 1000.0) / (double)(now - since) + 0.5);
            const int asked = g_displayRate.load();
            if (asked > 0) LOGI("FPS=%d/%d", shown, asked);
        }
    }
    int64_t last = lastSaid.load();
    if (now - last < (now < 40000 ? 2000 : 30000) || !lastSaid.compare_exchange_strong(last, now)) return;
    std::string layers;
    for (uint32_t i = 0; info && info->layers && i < info->layerCount && i < 8; i++) {
        const XrCompositionLayerBaseHeader* header = info->layers[i];
        char piece[112];
        if (!header) {
            snprintf(piece, sizeof(piece), " null");
        } else if (header->type == XR_TYPE_COMPOSITION_LAYER_PROJECTION) {
            const auto* projection = reinterpret_cast<const XrCompositionLayerProjection*>(header);
            snprintf(piece, sizeof(piece), " projection(%u views, flags 0x%llx, floor space %d)", projection->viewCount, (unsigned long long)header->layerFlags, (int)FloorBased(header->space));
            LOGI("frame check, projection layer: structures chained to it:%s; frame time %lld ns", ChainTypes(header->next).c_str(), info ? (long long)info->displayTime : -1LL);
            for (uint32_t viewIndex = 0; projection->views && viewIndex < projection->viewCount && viewIndex < 2; viewIndex++) {
                const XrCompositionLayerProjectionView& v = projection->views[viewIndex];
                LOGI("frame check, view %u: at %.2f %.2f %.2f, turned %.2f %.2f %.2f %.2f, field of view %.2f %.2f %.2f %.2f, image %dx%d at %d,%d (array index %u), structures chained to it:%s",
                     viewIndex, v.pose.position.x, v.pose.position.y, v.pose.position.z, v.pose.orientation.x, v.pose.orientation.y, v.pose.orientation.z, v.pose.orientation.w,
                     v.fov.angleLeft, v.fov.angleRight, v.fov.angleUp, v.fov.angleDown, v.subImage.imageRect.extent.width, v.subImage.imageRect.extent.height,
                     v.subImage.imageRect.offset.x, v.subImage.imageRect.offset.y, v.subImage.imageArrayIndex, ChainTypes(v.next).c_str());
            }
        } else if (header->type == XR_TYPE_COMPOSITION_LAYER_QUAD) {
            snprintf(piece, sizeof(piece), " quad(flags 0x%llx)", (unsigned long long)header->layerFlags);
        } else {
            snprintf(piece, sizeof(piece), " type %d", (int)header->type);
        }
        layers += piece;
    }
    LOGI("frame check at %.0f s: %u frames, %u without a layer, %u told not to render; the last has %u layer(s):%s; blend mode %d; head in the floor space at %.2f %.2f %.2f (position %s)",
         now / 1000.0, g_frameCount.load(), g_emptyFrames.load(), g_noRenderFrames.load(), info ? info->layerCount : 0, layers.c_str(), info ? (int)info->environmentBlendMode : -1,
         g_headX.load(), g_headY.load(), g_headZ.load(), g_headValid.load() < 0 ? "not located yet" : g_headValid.load() ? "valid" : "not valid");
}

void LogEndFrameFailure(XrResult result, const XrFrameEndInfo* info) {
    if (g_endFrameFailures.fetch_add(1) >= 12) return;
    LOGI("xrEndFrame -> %d with %u layer(s), blend mode %d", (int)result, info->layerCount, (int)info->environmentBlendMode);
    for (uint32_t i = 0; i < info->layerCount; i++) {
        const XrCompositionLayerBaseHeader* header = info->layers[i];
        if (!header) { LOGI("  layer %u: null", i); continue; }
        if (header->type == XR_TYPE_COMPOSITION_LAYER_PROJECTION) {
            const auto* projection = reinterpret_cast<const XrCompositionLayerProjection*>(header);
            LOGI("  layer %u: projection, %u views, flags 0x%llx", i, projection->viewCount, (unsigned long long)header->layerFlags);
            for (uint32_t v = 0; v < projection->viewCount; v++) {
                const XrCompositionLayerProjectionView& view = projection->views[v];
                LOGI("    view %u: rect %d,%d %dx%d array %u fov %.2f/%.2f/%.2f/%.2f", v, view.subImage.imageRect.offset.x, view.subImage.imageRect.offset.y,
                     view.subImage.imageRect.extent.width, view.subImage.imageRect.extent.height, view.subImage.imageArrayIndex,
                     view.fov.angleLeft, view.fov.angleRight, view.fov.angleUp, view.fov.angleDown);
            }
        } else if (header->type == XR_TYPE_COMPOSITION_LAYER_QUAD) {
            const auto* quad = reinterpret_cast<const XrCompositionLayerQuad*>(header);
            Size known{0, 0};
            {
                std::lock_guard<std::mutex> lock(g_mutex);
                auto it = g_swapchainSize.find(quad->subImage.swapchain);
                if (it != g_swapchainSize.end()) known = it->second;
            }
            LOGI("  layer %u: quad, rect %dx%d size %.2fx%.2f flags 0x%llx, swapchain %ux%u, visibility %d", i, quad->subImage.imageRect.extent.width,
                 quad->subImage.imageRect.extent.height, quad->size.width, quad->size.height, (unsigned long long)header->layerFlags,
                 known.width, known.height, (int)quad->eyeVisibility);
        } else {
            LOGI("  layer %u: type %d", i, (int)header->type);
        }
    }
}

// Some engines submit quads backed by a 4x4 placeholder swapchain; a Quest runtime can refuse them.
bool IsTinyQuad(const XrCompositionLayerBaseHeader* header) {
    if (!header || header->type != XR_TYPE_COMPOSITION_LAYER_QUAD) return false;
    const auto* quad = reinterpret_cast<const XrCompositionLayerQuad*>(header);
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_swapchainSize.find(quad->subImage.swapchain);
    if (it != g_swapchainSize.end()) return it->second.width <= 16 && it->second.height <= 16;
    // A swapchain the layer never saw created: judge by the rectangle the game submits.
    return quad->subImage.imageRect.extent.width <= 16 && quad->subImage.imageRect.extent.height <= 16;
}

bool Droppable(int mode, const XrCompositionLayerBaseHeader* header) {
    if (!header) return false;
    if (mode == 1) return IsTinyQuad(header);
    if (mode == 2) return header->type == XR_TYPE_COMPOSITION_LAYER_QUAD;
    return false;
}

std::vector<const XrCompositionLayerBaseHeader*> Without(int mode, const std::vector<const XrCompositionLayerBaseHeader*>& all) {
    std::vector<const XrCompositionLayerBaseHeader*> kept;
    for (const auto* layer : all) if (!Droppable(mode, layer)) kept.push_back(layer);
    return kept;
}

// Test files of the game's folder (see GamePortFolder), read when the layer needs them: the first line of the file, empty when there is none.
std::string TestFileText(const char* file) {
    std::string text;
    FILE* in = fopen((GamePortFolder() + file).c_str(), "r");
    if (!in) return text;
    int c;
    while ((c = fgetc(in)) != EOF && c != '\n' && c != '\r' && text.size() < 400) text += (char)c;
    fclose(in);
    return text;
}

// What the player chose for the device's graphics in this game (the hook sets the environment from GamePort's settings), or, for a test, a file of the game's folder: the image size as
// a scale (1.0 leaves it), the frame rate in hertz (empty leaves it), and the levels of the processor and the graphics chip ("cpu=50 gpu=50", empty leaves them to the system).
// The side of the image GamePort asks for, in pixels (0 leaves the size to the game); the hook sets it from the player's choice, a share of the screen's resolution.
int ChosenResolutionSide() {
    const char* chosen = getenv("GAMEPORT_XR_RESOLUTION");
    const int side = chosen ? atoi(chosen) : 0;
    return side >= 256 && side <= 4096 ? side : 0;
}

int ChosenResolutionHeight() {
    const char* chosen = getenv("GAMEPORT_XR_RESOLUTION_HEIGHT");
    const int side = chosen ? atoi(chosen) : 0;
    return side >= 64 && side <= 4096 ? side : 0;
}

double ChosenResolutionScale() {
    const std::string text = TestFileText("xr_resolution_scale.txt");
    return text.empty() ? 1.0 : atof(text.c_str());
}

std::string ChosenRefreshRate() {
    const char* chosen = getenv("GAMEPORT_XR_REFRESH");
    const int rate = chosen ? atoi(chosen) : 0;
    return rate > 0 ? std::to_string(rate) : TestFileText("xr_refresh_rate.txt");
}

std::string ChosenLevels() {
    const char* cpu = getenv("GAMEPORT_XR_CPU_LEVEL");
    const char* gpu = getenv("GAMEPORT_XR_GPU_LEVEL");
    const int cpuLevel = cpu ? atoi(cpu) : -1, gpuLevel = gpu ? atoi(gpu) : -1;
    if (cpuLevel >= 0 || gpuLevel >= 0) {
        std::string text;
        if (cpuLevel >= 0) text += "cpu=" + std::to_string(cpuLevel) + " ";
        if (gpuLevel >= 0) text += "gpu=" + std::to_string(gpuLevel);
        return text;
    }
    return TestFileText("xr_perf_levels.txt");
}

// What the headset offers for showing the real world behind the game (1 opaque, 2 additive, 3 alpha blend): a game that wants its passthrough asks for the alpha blend one.
XrResult XRAPI_CALL Layer_xrEnumerateEnvironmentBlendModes(XrInstance instance, XrSystemId systemId, XrViewConfigurationType type, uint32_t capacity, uint32_t* countOut, XrEnvironmentBlendMode* modes) {
    // Test file xr_blend_opaque_only.txt: the game is told the headset has the opaque mode only.
    if (countOut && !TestFileText("xr_blend_opaque_only.txt").empty()) {
        static std::atomic<bool> told{false};
        if (!told.exchange(true)) LOGI("the environment blend modes are cut to the opaque one (test file)");
        *countOut = 1;
        if (capacity == 0) return XR_SUCCESS;
        if (modes) modes[0] = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        return XR_SUCCESS;
    }
    if (!countOut || !g_offerAlphaBlend.load()) {
        const XrResult result = g_enumerateBlendModes(instance, systemId, type, capacity, countOut, modes);
        if (XR_SUCCEEDED(result) && countOut && modes && capacity > 0) {
            std::string list;
            for (uint32_t i = 0; i < *countOut && i < capacity; i++) list += " " + std::to_string((int)modes[i]);
            LOGI("the runtime offers the environment blend modes:%s (1 opaque, 2 additive, 3 alpha blend)", list.c_str());
        }
        return result;
    }
    // The whole list, to know what the runtime offers and to add to it.
    uint32_t count = 0;
    XrResult result = g_enumerateBlendModes(instance, systemId, type, 0, &count, nullptr);
    if (XR_FAILED(result)) return result;
    std::vector<XrEnvironmentBlendMode> list(count);
    result = g_enumerateBlendModes(instance, systemId, type, count, &count, list.data());
    if (XR_FAILED(result)) return result;
    list.resize(count);
    bool offered = false;
    for (auto mode : list) if (mode == XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND) offered = true;
    std::string text;
    for (auto mode : list) text += " " + std::to_string((int)mode);
    if (!offered && type == XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO) {
        list.push_back(XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND);
        g_alphaOffered.store(true);
        if (capacity > 0) LOGI("the runtime offers the environment blend modes:%s; the alpha blend mode is added for the game (passthrough chosen)", text.c_str());
    } else if (capacity > 0) {
        LOGI("the runtime offers the environment blend modes:%s (1 opaque, 2 additive, 3 alpha blend)", text.c_str());
    }
    *countOut = (uint32_t)list.size();
    if (capacity == 0) return XR_SUCCESS;
    if (capacity < list.size()) return XR_ERROR_SIZE_INSUFFICIENT;
    for (size_t i = 0; i < list.size(); i++) modes[i] = list[i];
    return XR_SUCCESS;
}

XrResult XRAPI_CALL Layer_xrEndFrame(XrSession session, const XrFrameEndInfo* endInfo) {
    // Said once, and again when it changes: how the game wants the real world to show behind its image.
    {
        static std::atomic<int> lastBlendMode{-1};
        if (endInfo && lastBlendMode.exchange((int)endInfo->environmentBlendMode) != (int)endInfo->environmentBlendMode) {
            LOGI("the game's environment blend mode is now %d (1 opaque, 2 additive, 3 alpha blend)", (int)endInfo->environmentBlendMode);
        }
    }
    FrameCheck(endInfo);
    if (!endInfo || endInfo->layerCount == 0) return g_endFrame(session, endInfo);
    const bool shifting = g_offset.load() != 0.0f;

    // Copies of the layers that need changing: the shift undone for anything the game placed in a
    // floor-based space, and empty image rectangles replaced by the swapchain's real size.
    std::vector<XrCompositionLayerProjection> projections;
    std::vector<std::vector<XrCompositionLayerProjectionView>> projectionViews;
    std::vector<XrCompositionLayerQuad> quads;
    std::vector<const XrCompositionLayerBaseHeader*> layers(endInfo->layerCount);
    projections.reserve(endInfo->layerCount);
    projectionViews.reserve(endInfo->layerCount);
    quads.reserve(endInfo->layerCount);

    // Test aid: with "xr_hide_depth_test.txt" present (in the folder above), a depth test chained to the projection layer (telling the compositor how to
    // test this layer's depth against other system layers) is left out of what the runtime is given, to see whether it has anything to do with the layer
    // failing to show at all. Only the common case is handled, it being the first structure chained (the only order seen so far); a less usual chain is
    // left untouched rather than risk breaking it.
    auto stripDepthTestEnabled = []() {
        static std::atomic<int> cached{-1};
        int value = cached.load();
        if (value < 0) {
            FILE* marker = fopen((GamePortFolder() + "xr_hide_depth_test.txt").c_str(), "r");
            value = marker ? 1 : 0;
            if (marker) fclose(marker);
            cached.store(value);
        }
        return value == 1;
    };
    for (uint32_t i = 0; i < endInfo->layerCount; i++) {
        const XrCompositionLayerBaseHeader* header = endInfo->layers[i];
        layers[i] = header;
        if (!header) continue;
        if (header->type == XR_TYPE_COMPOSITION_LAYER_PROJECTION) {
            const auto* source = reinterpret_cast<const XrCompositionLayerProjection*>(header);
            std::vector<XrCompositionLayerProjectionView> views(source->views, source->views + source->viewCount);
            bool changed = false;
            const bool shift = shifting && FloorBased(header->space);
            for (auto& view : views) {
                if (FixRect(view.subImage.swapchain, view.subImage)) changed = true;
                if (shift) { AddOffset(view.pose, -1.0f); changed = true; }
            }
            const auto* firstLink = reinterpret_cast<const XrBaseInStructure*>(header->next);
            const bool stripDepth = stripDepthTestEnabled() && firstLink && firstLink->type == XR_TYPE_COMPOSITION_LAYER_DEPTH_TEST_FB;
            if (!changed && !stripDepth) continue;
            projectionViews.push_back(std::move(views));
            projections.push_back(*source);
            projections.back().views = projectionViews.back().data();
            if (stripDepth) {
                static std::atomic<bool> logged{false};
                if (!logged.exchange(true)) LOGI("the depth test chained to the projection layer is not given to the runtime (test file present)");
                projections.back().next = firstLink->next;
            }
            layers[i] = reinterpret_cast<const XrCompositionLayerBaseHeader*>(&projections.back());
        } else if (header->type == XR_TYPE_COMPOSITION_LAYER_QUAD) {
            XrCompositionLayerQuad quad = *reinterpret_cast<const XrCompositionLayerQuad*>(header);
            bool changed = FixRect(quad.subImage.swapchain, quad.subImage);
            if (shifting && FloorBased(header->space)) { AddOffset(quad.pose, -1.0f); changed = true; }
            if (!changed) continue;
            quads.push_back(quad);
            layers[i] = reinterpret_cast<const XrCompositionLayerBaseHeader*>(&quads.back());
        }
    }
    std::vector<const XrCompositionLayerBaseHeader*> submitted = Without(g_dropMode.load(), layers);
    // The game asks for the real world behind its image (the alpha blend mode): the headset's passthrough layer goes first, the game's layers over it blending by their alpha.
    XrCompositionLayerPassthroughFB passthroughLayer{XR_TYPE_COMPOSITION_LAYER_PASSTHROUGH_FB};
    std::vector<XrCompositionLayerProjection> blendedProjections;
    std::vector<XrCompositionLayerQuad> blendedQuads;
    std::vector<const XrCompositionLayerBaseHeader*> overPassthrough;
    // Only where the layer itself offered the mode: a game the runtime already gives the alpha blend mode to (Cubism) is left exactly as it was.
    const bool wantsReal = g_offerAlphaBlend.load() && g_alphaOffered.load() && endInfo->environmentBlendMode == XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND && EnsurePassthrough(session);
    if (wantsReal) {
        passthroughLayer.flags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
        passthroughLayer.space = XR_NULL_HANDLE;
        passthroughLayer.layerHandle = g_passthroughLayer;
        blendedProjections.reserve(submitted.size());
        blendedQuads.reserve(submitted.size());
        overPassthrough.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader*>(&passthroughLayer));
        for (const XrCompositionLayerBaseHeader* header : submitted) {
            if (header && header->type == XR_TYPE_COMPOSITION_LAYER_PROJECTION) {
                blendedProjections.push_back(*reinterpret_cast<const XrCompositionLayerProjection*>(header));
                blendedProjections.back().layerFlags |= XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
                overPassthrough.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader*>(&blendedProjections.back()));
            } else if (header && header->type == XR_TYPE_COMPOSITION_LAYER_QUAD) {
                blendedQuads.push_back(*reinterpret_cast<const XrCompositionLayerQuad*>(header));
                blendedQuads.back().layerFlags |= XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
                overPassthrough.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader*>(&blendedQuads.back()));
            } else {
                overPassthrough.push_back(header);
            }
        }
        submitted = overPassthrough;
        static std::atomic<bool> told{false};
        if (!told.exchange(true)) LOGI("passthrough: frames go out with the headset's passthrough layer under the game's (%u layer(s) in all)", (unsigned)submitted.size());
    }
    // A Pico gives the alpha blend mode to the game itself, but takes the alpha of a layer into account only when the layer says so (the source alpha bit): Unreal's
    // projection layer does not, so the game's real world stayed black. Only for a game the player chose the passthrough for, and only for the layers that lack the bit.
    std::vector<XrCompositionLayerProjection> alphaProjections;
    if (!wantsReal && g_alphaByLayerBit.load() && endInfo->environmentBlendMode == XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND) {
        alphaProjections.reserve(submitted.size());
        for (auto& header : submitted) {
            if (!header || header->type != XR_TYPE_COMPOSITION_LAYER_PROJECTION) continue;
            const auto* projection = reinterpret_cast<const XrCompositionLayerProjection*>(header);
            if (projection->layerFlags & XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT) continue;
            alphaProjections.push_back(*projection);
            alphaProjections.back().layerFlags |= XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
            header = reinterpret_cast<const XrCompositionLayerBaseHeader*>(&alphaProjections.back());
            static std::atomic<bool> told{false};
            if (!told.exchange(true)) LOGI("passthrough: the projection layer of the game now says it blends by its alpha (flags 0x%x -> 0x%x)", (unsigned)projection->layerFlags, (unsigned)alphaProjections.back().layerFlags);
        }
    }
    XrFrameEndInfo shifted = *endInfo;
    shifted.layers = submitted.data();
    shifted.layerCount = (uint32_t)submitted.size();
    if (wantsReal) shifted.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    // The alpha blend mode the layer offered: if the runtime refuses a frame in it, the frame goes out opaque (and every later one), so the game stays on screen.
    const bool alphaOffered = g_alphaOffered.load();
    if (alphaOffered && g_alphaRefused.load() && shifted.environmentBlendMode == XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND) shifted.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    XrResult result = g_endFrame(session, &shifted);
    if (alphaOffered && XR_FAILED(result) && shifted.environmentBlendMode == XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND) {
        XrFrameEndInfo opaque = shifted;
        opaque.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        const XrResult second = g_endFrame(session, &opaque);
        LOGI("the runtime refused a frame in the alpha blend mode (%d); sent opaque -> %d", (int)result, (int)second);
        if (XR_SUCCEEDED(second)) { g_alphaRefused.store(true); return second; }
    } else if (alphaOffered && XR_SUCCEEDED(result) && shifted.environmentBlendMode == XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND) {
        static std::atomic<bool> told{false};
        if (!told.exchange(true)) LOGI("the runtime accepted a frame in the alpha blend mode");
    }
    if (result == XR_ERROR_LAYER_INVALID) {
        for (int mode = g_dropMode.load() + 1; mode <= 2; mode++) {
            std::vector<const XrCompositionLayerBaseHeader*> kept = Without(mode, layers);
            if (kept.size() == layers.size()) { LOGI("xrEndFrame refused the layers; mode %d has nothing to drop", mode); continue; }
            XrFrameEndInfo retry = *endInfo;
            retry.layers = kept.data();
            retry.layerCount = (uint32_t)kept.size();
            XrResult second = g_endFrame(session, &retry);
            LOGI("xrEndFrame refused the layers; mode %d (%u of %u layer(s) dropped) -> %d", mode, (unsigned)(layers.size() - kept.size()), (unsigned)layers.size(), (int)second);
            if (XR_SUCCEEDED(second)) { g_dropMode.store(mode); return second; }
        }
    }
    if (XR_FAILED(result)) LogEndFrameFailure(result, &shifted);
    return result;
}

// Once per session, measure the real head height and work out the gap to the standing eye height.
void Calibrate(XrTime time) {
    if (!g_seated || !g_calibrating || g_session == XR_NULL_HANDLE) return;
    if (g_ownStage == XR_NULL_HANDLE) {
        XrReferenceSpaceCreateInfo info{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
        info.poseInReferenceSpace = IdentityPose();
        info.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_STAGE;
        if (g_createReferenceSpace(g_session, &info, &g_ownStage) != XR_SUCCESS) { g_ownStage = XR_NULL_HANDLE; return; }
        info.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_VIEW;
        if (g_createReferenceSpace(g_session, &info, &g_ownView) != XR_SUCCESS) { g_ownView = XR_NULL_HANDLE; return; }
    }
    XrSpaceLocation location{XR_TYPE_SPACE_LOCATION};
    if (g_locateSpace(g_ownView, g_ownStage, time, &location) != XR_SUCCESS) return;
    if (!(location.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT)) return;
    g_sum += location.pose.position.y;
    if (++g_samples < kCalibrationSamples) return;

    float measured = (float)(g_sum / g_samples);
    float gap = g_targetEyeMeters - measured;
    float offset = (gap < kAlreadyStandingMeters) ? 0.0f : std::min(gap, kMaxOffsetMeters);
    g_offset.store(offset);
    g_calibrating = false;
    LOGI("seated calibration: head at %.2f m, target %.2f m, offset %.2f m", measured, g_targetEyeMeters, offset);
}

long long NowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

// Leaves what is known of the frame rate in a file of the game's folder, which the hook hands to GamePort: the rate asked for, whether the runtime accepted it, the rates it
// accepts, the rate the game asked for itself, and the one the frames really come at. Written when the session begins and again a little later, once the rate has settled.
void WriteRefreshReport() {
    std::string accepted;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        accepted = g_refreshAccepted;
    }
    FILE* out = fopen((GamePortFolder() + "xr_refresh.txt").c_str(), "w");
    if (!out) return;
    fprintf(out, "asked=%d\nresult=%d\naccepted=%s\ngameasked=%d\nobserved=%d\n", g_refreshAsked.load(), g_refreshResult.load(), accepted.c_str(), g_refreshGameAsked.load(), g_displayRate.load());
    fclose(out);
}

// A game that asks for a frame rate of its own: said, and passed on as it is.
XrResult XRAPI_CALL Layer_xrRequestDisplayRefreshRateFB(XrSession session, float rate) {
    if (!g_refreshRequest) return XR_ERROR_FUNCTION_UNSUPPORTED;
    g_refreshGameAsked.store((int)(rate + 0.5f));
    const XrResult result = g_refreshRequest(session, rate);
    LOGI("the game asked for a display refresh rate of %.1f -> %d", rate, (int)result);
    return result;
}

XrResult XRAPI_CALL Layer_xrWaitFrame(XrSession session, const XrFrameWaitInfo* info, XrFrameState* state) {
    XrResult result = g_waitFrame(session, info, state);
    // The rate the frames come at is read some seconds after the session began, then once more: it says whether what was asked for held.
    if (g_refreshAsked.load() > 0) {
        const long long since = NowMs() - g_sessionStartMs.load();
        const int stage = g_refreshStage.load();
        if ((stage == 0 && since > 8000) || (stage == 1 && since > 60000)) {
            g_refreshStage.store(stage + 1);
            WriteRefreshReport();
        }
    }
    if (XR_SUCCEEDED(result) && state && state->predictedDisplayPeriod > 0) g_displayRate.store((int)(1e9 / (double)state->predictedDisplayPeriod + 0.5));
    if (XR_SUCCEEDED(result) && state) Calibrate(state->predictedDisplayTime);
    if (XR_SUCCEEDED(result) && state && !state->shouldRender) g_noRenderFrames.fetch_add(1);
    return result;
}

XrResult XRAPI_CALL Layer_xrBeginSession(XrSession session, const XrSessionBeginInfo* info) {
    XrResult result = g_beginSession(session, info);
    LOGI("xrBeginSession(view configuration %d) -> %d", info ? (int)info->primaryViewConfigurationType : -1, (int)result);
    if (XR_SUCCEEDED(result)) {
        g_session = session;
        g_samples = 0;
        g_sum = 0.0;
        g_calibrating = g_seated;
        g_offset.store(0.0f);
        // Test files xr_perf_levels.txt ("cpu=50 gpu=50": 0 power saving, 25 sustained low, 50 sustained high, 75 boost) and xr_refresh_rate.txt (72 or 90): asked of the runtime
        // as the session begins, to see what they change of the picture and of the frame rate.
        const std::string levels = ChosenLevels();
        if (!levels.empty() && g_perfLevel) {
            const char* cpu = strstr(levels.c_str(), "cpu=");
            const char* gpu = strstr(levels.c_str(), "gpu=");
            if (cpu) LOGI("performance level of the cpu set to %d (test file) -> %d", atoi(cpu + 4), (int)g_perfLevel(session, XR_PERF_SETTINGS_DOMAIN_CPU_EXT, (XrPerfSettingsLevelEXT)atoi(cpu + 4)));
            if (gpu) LOGI("performance level of the gpu set to %d (test file) -> %d", atoi(gpu + 4), (int)g_perfLevel(session, XR_PERF_SETTINGS_DOMAIN_GPU_EXT, (XrPerfSettingsLevelEXT)atoi(gpu + 4)));
        } else if (!levels.empty()) {
            LOGI("performance levels asked for (test file) and the runtime has no function for them (the extension is not enabled)");
        }
        const std::string refresh = ChosenRefreshRate();
        if (!refresh.empty() && g_refreshRequest) {
            const float rate = (float)atof(refresh.c_str());
            const XrResult asked = g_refreshRequest(session, rate);
            LOGI("display refresh rate %.1f asked for (test file) -> %d", rate, (int)asked);
            g_refreshAsked.store((int)(rate + 0.5f));
            g_refreshResult.store((int)asked);
            g_sessionStartMs.store(NowMs());
            g_refreshStage.store(0);
            // The rates the runtime accepts: what GamePort offers the player is made of them.
            if (g_refreshEnumerate) {
                uint32_t count = 0;
                if (XR_SUCCEEDED(g_refreshEnumerate(session, 0, &count, nullptr)) && count > 0 && count < 64) {
                    std::vector<float> rates(count);
                    if (XR_SUCCEEDED(g_refreshEnumerate(session, count, &count, rates.data()))) {
                        std::string list;
                        for (uint32_t i = 0; i < count && i < rates.size(); i++) list += (i ? "," : "") + std::to_string((int)(rates[i] + 0.5f));
                        LOGI("display refresh rates the runtime accepts: %s", list.c_str());
                        std::lock_guard<std::mutex> lock(g_mutex);
                        g_refreshAccepted = list;
                    }
                }
            }
            WriteRefreshReport();
        } else if (!refresh.empty()) {
            LOGI("a refresh rate asked for (test file) and the runtime has no function for it (the extension is not enabled)");
        }
    }
    return result;
}

// Size of the last eye swapchain the runtime accepted. Some games pick a plugin written for another
// headset, which then asks for swapchains of 0x0; the runtime refuses those and the game quits.
uint32_t g_lastEyeWidth = 0;
uint32_t g_lastEyeHeight = 0;

XrResult XRAPI_CALL Layer_xrCreateSwapchain(XrSession session, const XrSwapchainCreateInfo* info, XrSwapchain* swapchain) {
    XrSwapchainCreateInfo fixed;
    XrSwapchainCreateInfo masked;
    // Test file xr_swapchain_usage_clear.txt (a hexadecimal mask): these usages are taken off every swapchain before the runtime sees it, as OVRPort's loader does for 0xdc.
    if (info) {
        const std::string clear = TestFileText("xr_swapchain_usage_clear.txt");
        if (!clear.empty()) {
            const unsigned long long mask = strtoull(clear.c_str(), nullptr, 16);
            if (mask != 0 && (info->usageFlags & mask) != 0) {
                masked = *info;
                masked.usageFlags &= ~(XrSwapchainUsageFlags)mask;
                LOGI("xrCreateSwapchain: usage 0x%llx made 0x%llx (test file, mask 0x%llx)", (unsigned long long)info->usageFlags, (unsigned long long)masked.usageFlags, mask);
                info = &masked;
            }
        }
    }
    if (info && (info->width == 0 || info->height == 0) && g_lastEyeWidth > 0 && g_lastEyeHeight > 0) {
        fixed = *info;
        fixed.width = g_lastEyeWidth;
        fixed.height = g_lastEyeHeight;
        LOGI("xrCreateSwapchain asked for %ux%u; using the eye size %ux%u instead", info->width, info->height, fixed.width, fixed.height);
        info = &fixed;
    }
    XrResult result = g_createSwapchain(session, info, swapchain);
    // A runtime may refuse a usage the game asks for out of habit (Pico's: "contains invalid flags" for an input attachment, which Metro Awakening asks for), and the game is
    // then left with no image at all. Only after a refusal, the swapchain is asked for again with fewer usages: first without the input attachment, then also without the
    // copies and the unordered access. What the game really needs (color, depth, sampling) stays.
    XrSwapchainCreateInfo reduced;
    if (XR_FAILED(result) && info && swapchain) {
        constexpr XrSwapchainUsageFlags kInputAttachment = 0x80;
        constexpr XrSwapchainUsageFlags kCopies = 0x04 | 0x08 | 0x10;  // unordered access, transfer source, transfer destination
        const XrSwapchainUsageFlags steps[] = {info->usageFlags & ~kInputAttachment, info->usageFlags & ~(kInputAttachment | kCopies)};
        XrSwapchainUsageFlags last = info->usageFlags;
        for (XrSwapchainUsageFlags usage : steps) {
            if (usage == last || usage == 0) continue;
            last = usage;
            reduced = *info;
            reduced.usageFlags = usage;
            const XrResult again = g_createSwapchain(session, &reduced, swapchain);
            LOGI("xrCreateSwapchain refused usage 0x%llx (%d): asked again with 0x%llx -> %d", (unsigned long long)info->usageFlags, (int)result,
                 (unsigned long long)usage, (int)again);
            if (XR_SUCCEEDED(again)) {
                result = again;
                info = &reduced;
                break;
            }
        }
    }
    if (XR_SUCCEEDED(result) && info && swapchain) {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_swapchainSize[*swapchain] = {info->width, info->height};
    }
    if (XR_SUCCEEDED(result) && info && info->arraySize == 2 && info->width > 64 && info->height > 64) {
        g_lastEyeWidth = info->width;
        g_lastEyeHeight = info->height;
    }
    if (info) {
        LOGI("xrCreateSwapchain -> %d: usage=0x%llx format=%lld samples=%u size=%ux%u faces=%u array=%u mips=%u flags=0x%llx",
             (int)result, (unsigned long long)info->usageFlags, (long long)info->format, info->sampleCount, info->width, info->height,
             info->faceCount, info->arraySize, info->mipCount, (unsigned long long)info->createFlags);
    }
    return result;
}

XrResult XRAPI_CALL Layer_xrDestroySwapchain(XrSwapchain swapchain) {
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_swapchainSize.erase(swapchain);
    }
    return g_destroySwapchain(swapchain);
}

// Report what the runtime recommends, and never let a view size be zero.
XrResult XRAPI_CALL Layer_xrEnumerateViewConfigurationViews(XrInstance instance, XrSystemId systemId, XrViewConfigurationType type,
                                                            uint32_t capacity, uint32_t* countOut, XrViewConfigurationView* views) {
    XrResult result = g_enumerateViews(instance, systemId, type, capacity, countOut, views);
    if (XR_SUCCEEDED(result) && views && countOut) {
        for (uint32_t i = 0; i < *countOut && i < capacity; i++) {
            // Some games ask at every frame: a line is written the first time a size is seen, not each time (it filled most of the start of the log).
            char line[200];
            snprintf(line, sizeof(line), "view configuration %d view %u: recommended %ux%u, max %ux%u, samples %u/%u", (int)type, i, views[i].recommendedImageRectWidth,
                     views[i].recommendedImageRectHeight, views[i].maxImageRectWidth, views[i].maxImageRectHeight,
                     views[i].recommendedSwapchainSampleCount, views[i].maxSwapchainSampleCount);
            static std::mutex seenMutex;
            static std::set<std::string> seen;
            bool first;
            {
                std::lock_guard<std::mutex> lock(seenMutex);
                first = seen.insert(line).second;
            }
            if (first) LOGI("%s", line);
            if (views[i].recommendedImageRectWidth == 0) views[i].recommendedImageRectWidth = views[i].maxImageRectWidth;
            if (views[i].recommendedImageRectHeight == 0) views[i].recommendedImageRectHeight = views[i].maxImageRectHeight;
            // The image size given to the game is the recommended one times the player's scale (the file xr_resolution_scale.txt for a test), never odd. The maximum the runtime
            // announces is not held to: it accepted a swapchain of 3072 by 3072 in a game while announcing 2560, so only a margin of 4096 a side keeps a choice from asking for the impossible.
            // The player's choice is an image size in pixels (the hook gives the width, and the height when GamePort knows the screen's); a test file gives a scale instead.
            const int chosenSide = ChosenResolutionSide(), chosenHeight = ChosenResolutionHeight();
            const double scale = chosenSide > 0 ? 1.0 : ChosenResolutionScale();
            const uint32_t recW = views[i].recommendedImageRectWidth, recH = views[i].recommendedImageRectHeight;
            uint32_t w = recW, h = recH;
            if (chosenSide > 0 && recW > 0) {
                w = std::max<uint32_t>(64, (uint32_t)chosenSide & ~1u);
                h = chosenHeight > 0 ? (uint32_t)chosenHeight : (uint32_t)((double)recH * chosenSide / recW + 0.5);
                h = std::min<uint32_t>(4096, std::max<uint32_t>(64, h & ~1u));
            } else if (scale >= 0.1 && scale <= 8.0 && scale != 1.0) {
                auto scaled = [scale](uint32_t recommended) {
                    uint32_t value = (uint32_t)(recommended * scale + 0.5);
                    if (value > 4096) value = 4096;
                    return std::max<uint32_t>(64, value & ~1u);
                };
                w = scaled(recW);
                h = scaled(recH);
            }
            if (w != recW || h != recH) {
                static std::atomic<bool> told{false};
                if (!told.exchange(true)) LOGI("the size given to the game is %ux%u instead of %ux%u (the player's choice or a test file)", w, h, recW, recH);
                views[i].recommendedImageRectWidth = w;
                views[i].recommendedImageRectHeight = h;
            }
        }
    }
    return result;
}

// Test aid: a game that writes an already gamma-corrected image into a swapchain the runtime made sRGB gets it gamma-corrected a second time by the
// hardware, which can crush a dim scene to black with no error anywhere (the layer, the runtime and the game all do exactly what they were asked). With
// "xr_hide_srgb_formats.txt" present (in the folder above, empty or not, only its presence matters) the sRGB formats are not offered, so the game is left
// to pick a plain one instead.
XrResult XRAPI_CALL Layer_xrEnumerateSwapchainFormats(XrSession session, uint32_t capacity, uint32_t* countOut, int64_t* formats) {
    XrResult result = g_enumerateSwapchainFormats(session, capacity, countOut, formats);
    if (!XR_SUCCEEDED(result) || !countOut) return result;
    FILE* marker = fopen((GamePortFolder() + "xr_hide_srgb_formats.txt").c_str(), "r");
    if (!marker) return result;
    fclose(marker);
    // Only R8G8B8A8_SRGB, the one the game actually picks on this device: hiding every sRGB format at once made it crash instead of falling back to a
    // plain one, so the engine needs at least one of them; this leaves it the others (its own order of preference may still land on another sRGB format).
    auto isSrgb = [](int64_t f) { return f == 43; };
    // First call (capacity 0): report the count without the hidden ones. Second call: the runtime already wrote the full list into formats; compact it
    // in place, dropping the hidden ones, and correct the count handed back.
    if (capacity == 0 || !formats) {
        uint32_t full = 0;
        std::vector<int64_t> probe(*countOut);
        if (*countOut > 0) g_enumerateSwapchainFormats(session, (uint32_t)probe.size(), &full, probe.data());
        uint32_t kept = 0;
        for (int64_t f : probe) if (!isSrgb(f)) kept++;
        *countOut = kept;
        return result;
    }
    uint32_t kept = 0;
    std::string removed;
    for (uint32_t i = 0; i < *countOut; i++) {
        if (isSrgb(formats[i])) { removed += " " + std::to_string(formats[i]); continue; }
        formats[kept++] = formats[i];
    }
    static std::atomic<bool> logged{false};
    if (!removed.empty() && !logged.exchange(true)) LOGI("swapchain formats: sRGB ones not offered (test file present), the game is left with a plain one instead:%s", removed.c_str());
    *countOut = kept;
    return result;
}

// --- Controller emulation -----------------------------------------------------------------------
//
// A game may know no controller profile of the device it runs on: a Steam Frame build knows Valve's, a
// Quest game knows Meta's, a Pico game knows Pico's. Once the game has told the runtime everything it
// knows (right before its action sets are attached), the layer decides, in this order:
//   1. the game has bindings for a profile of this device: nothing to do (the log says how its Steam Frame table compares, when it has one);
//   2. else the game has bindings for the Meta (Touch) controllers: they are translated onto the device;
//   3. else the game has bindings for the Steam Frame's controllers: they are translated onto the device.
// A translation moves each control (a hand and a group: "left:thumbstick", "right:a") onto the device's
// own; the player can override any control per game (GAMEPORT_XR_MAP, filled from GamePort's controller
// page): "left:dpad_up=left:y+left:x;right:menu=left:menu;left:view=none" (a control may go to several, joined by +). The game is told that the profile it
// knows is the active one, so it keeps reading the controllers.
// The device family comes from GamePort (GAMEPORT_XR_FAMILY, from its VrPlatform: "meta" or "pico"), else
// from the runtime's name.

enum class Family { Unknown, Meta, Pico };

const char* const kMetaProfiles[] = {"/interaction_profiles/meta/touch_controller_plus", "/interaction_profiles/facebook/touch_controller_pro",
                                     "/interaction_profiles/oculus/touch_controller"};
const char* const kPicoProfiles[] = {"/interaction_profiles/bytedance/pico4_controller", "/interaction_profiles/bytedance/pico_neo3_controller"};

Family g_family = Family::Unknown;
std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>> g_stash;  // every suggestion of the game, by profile (g_mutex)
std::atomic<bool> g_emulating{false};
std::atomic<bool> g_gameHasBoth{false};  // the game has its own controls for the device's controllers and the Steam Frame's
std::atomic<bool> g_preferFrame{false};  // the player asked for the Steam Frame's controls even when the game has its own for the device (GAMEPORT_XR_PREFER_FRAME)
// The controllers the player chose to present to the game (GAMEPORT_XR_PRESENT): "auto" is the layer's own choice (what it did before the player could choose), "frame" the
// Steam Frame's controls (the same path as GAMEPORT_XR_PREFER_FRAME), "touch" the Touch controls translated onto a Pico's when the game has none of its own for it.
enum class Present { Auto, Frame, Touch };
std::atomic<int> g_present{static_cast<int>(Present::Auto)};
std::string g_usedKind;    // the kind of table a translation succeeded for ("valve" or "touch"), empty when none did (the thread that attaches the game's actions)
std::string g_usedSource;  // and its profile

// Strict: only "auto", "frame" and "touch" count; absent, empty or anything else is "auto" ([unknown] says the value was not empty and not understood).
Present ParsePresent(const char* text, bool* unknown) {
    if (unknown) *unknown = false;
    if (!text || !text[0]) return Present::Auto;
    if (strcmp(text, "auto") == 0) return Present::Auto;
    if (strcmp(text, "frame") == 0) return Present::Frame;
    if (strcmp(text, "touch") == 0) return Present::Touch;
    if (unknown) *unknown = true;
    return Present::Auto;
}

const char* PresentName(Present present) { return present == Present::Frame ? "frame" : present == Present::Touch ? "touch" : "auto"; }

// The strings the game gave xrStringToPath for interaction profiles: a runtime may not give a profile it does not know back (g_namesMutex).
std::mutex g_namesMutex;
std::unordered_map<XrPath, std::string> g_pathNames;
XrPath g_emulatedSourcePath = XR_NULL_PATH;  // the profile the game knows and is told is active
std::unordered_map<std::string, XrPath> g_profileHandles;  // by profile name, the handle the game itself used for it (g_mutex)

std::string PathText(XrInstance instance, XrPath path) {
    char text[XR_MAX_PATH_LENGTH] = {0};
    uint32_t written = 0;
    if (g_pathToString && path != XR_NULL_PATH) g_pathToString(instance, path, sizeof(text), &written, text);
    if (text[0] == 0 && path != XR_NULL_PATH) {
        std::lock_guard<std::mutex> lock(g_namesMutex);
        auto known = g_pathNames.find(path);
        if (known != g_pathNames.end()) return known->second;
    }
    return text;
}

std::string Lower(std::string text) {
    for (auto& c : text) c = (char)tolower((unsigned char)c);
    return text;
}

bool IsNativeProfile(const std::string& profile) {
    if (g_family == Family::Meta) return profile.find("touch_controller") != std::string::npos;
    if (g_family == Family::Pico) return profile.find("pico") != std::string::npos;
    return false;
}

struct Control { std::string hand, group, sub; };

bool ParseInput(const std::string& path, Control& out) {
    for (const char* hand : {"left", "right"}) {
        const std::string prefix = std::string("/user/hand/") + hand + "/input/";
        if (path.compare(0, prefix.size(), prefix) != 0) continue;
        const std::string rest = path.substr(prefix.size());
        const size_t slash = rest.find('/');
        out.hand = hand;
        out.group = rest.substr(0, slash);
        out.sub = slash == std::string::npos ? "" : rest.substr(slash + 1);
        return true;
    }
    return false;
}

// Meta and Pico controllers share this layout: trigger, grip, joystick, thumb rest, X/Y and menu on the
// left, A/B on the right.
bool DeviceHasGroup(const std::string& hand, const std::string& group) { return gp::DeviceHasControl(hand, group); }

// The sub-input the device offers for a source sub-input: true with [out] set ("" meaning the group
// itself, as the joystick's two-axis value has no sub-input), or false when it has nothing for it.
bool DeviceSub(const std::string& group, const std::string& sub, std::string& out) { return gp::DeviceSubFor(group, sub, g_family == Family::Pico, out); }

bool IsValveProfile(const std::string& profile) { return profile.find("valve/frame_controller") != std::string::npos; }

// The controls ("hand:group") the game binds in [bindings], the poses left out.
std::vector<std::string> ControlsOf(XrInstance instance, const std::vector<XrActionSuggestedBinding>& bindings) {
    std::vector<std::string> keys;
    for (const XrActionSuggestedBinding& binding : bindings) {
        Control control;
        if (!ParseInput(PathText(instance, binding.binding), control) || control.group == "grip" || control.group == "aim") continue;
        const std::string key = control.hand + ":" + control.group;
        if (std::find(keys.begin(), keys.end(), key) == keys.end()) keys.push_back(key);
    }
    return keys;
}

// The folder GamePort reads what the layer leaves for it: the game's own files folder, "gameport" in it.
std::string GamePortFolder() {
    char cmd[256] = {0};
    FILE* in = fopen("/proc/self/cmdline", "r");
    if (!in) return "";
    size_t n = fread(cmd, 1, sizeof(cmd) - 1, in);
    fclose(in);
    cmd[n] = 0;
    return std::string("/storage/emulated/0/Android/data/") + cmd + "/files/gameport/";
}

// Test aid: the extensions named in "xr_hide_extensions.txt" (in the folder above, separated by commas or lines) are not passed on to the runtime, to find which optional extension a
// game's black screen depends on.
std::set<std::string> HiddenExtensions() {
    std::set<std::string> names;
    FILE* in = fopen((GamePortFolder() + "xr_hide_extensions.txt").c_str(), "r");
    if (!in) return names;
    std::string name;
    int c;
    while ((c = fgetc(in)) != EOF) {
        if (c == ',' || c == '\n' || c == '\r' || c == ' ' || c == ';') {
            if (!name.empty()) names.insert(name);
            name.clear();
        } else {
            name += (char)c;
        }
    }
    if (!name.empty()) names.insert(name);
    fclose(in);
    return names;
}

std::atomic<uint32_t> g_actionPolls{0};
std::atomic<uint32_t> g_actionActive{0};
std::atomic<int64_t> g_firstPollMs{0};
std::atomic<bool> g_silentChecked{false};
std::atomic<bool> g_silentMarked{false};

// Counts what the runtime answers when the game asks for the state of its actions. A game whose actions are all inactive after half a minute cannot hear the
// controllers: a marker is left for GamePort (and taken back if an action ever becomes active).
void NoteActionAnswer(bool active) {
    const int64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
    int64_t zero = 0;
    g_firstPollMs.compare_exchange_strong(zero, now);
    const uint32_t polls = g_actionPolls.fetch_add(1) + 1;
    const uint32_t actives = active ? g_actionActive.fetch_add(1) + 1 : g_actionActive.load();
    if (active && g_silentMarked.exchange(false)) {
        LOGI("an action of the game became active: the marker that the game cannot hear the controllers is taken back");
        remove((GamePortFolder() + "xr_silent.txt").c_str());
    remove((GamePortFolder() + "xr_stage_fallback.txt").c_str());
    }
    const int64_t elapsed = now - g_firstPollMs.load();
    if (!g_silentChecked.load() && elapsed >= 30000 && !g_silentChecked.exchange(true)) {
        const bool silent = gp::ActionsLookSilent(polls, actives, elapsed);
        LOGI("input check after %lld s: %u answers about the game's actions, %u active%s", (long long)(elapsed / 1000), polls, actives,
             silent ? ": none, the game cannot hear the controllers" : "");
        if (silent) {
            FILE* out = fopen((GamePortFolder() + "xr_silent.txt").c_str(), "w");
            if (out) {
                fputs("1\n", out);
                fclose(out);
                g_silentMarked.store(true);
            }
        }
    }
}

XrResult XRAPI_CALL Layer_xrGetActionStateBoolean(XrSession session, const XrActionStateGetInfo* info, XrActionStateBoolean* state) {
    XrResult result = g_getActionBoolean(session, info, state);
    if (XR_SUCCEEDED(result) && state) NoteActionAnswer(state->isActive == XR_TRUE);
    return result;
}

XrResult XRAPI_CALL Layer_xrGetActionStateFloat(XrSession session, const XrActionStateGetInfo* info, XrActionStateFloat* state) {
    XrResult result = g_getActionFloat(session, info, state);
    if (XR_SUCCEEDED(result) && state) NoteActionAnswer(state->isActive == XR_TRUE);
    return result;
}

// Tells GamePort which family the game was translated from and which controls it uses, so the controller page is offered for this game; and which
// controls of the device take more than one control of the game. "none" as the family says there is nothing to remap, so an old offer goes. "both":
// the game has its own controls for the device and the Steam Frame's, and the player may choose the second.
void WriteDetectedControls(const char* source, const std::vector<std::string>& groups, const std::vector<std::string>& shared) {
    const std::string folder = GamePortFolder();
    if (folder.empty()) return;
    FILE* out = fopen((folder + "xr_controls.txt").c_str(), "w");
    if (!out) return;
    fprintf(out, "source=%s\n", source);
    // Said every time the file is written, whoever writes it: a file that forgot it would take the choice away from the player.
    if (g_gameHasBoth.load()) fprintf(out, "both=1\n");
    for (const auto& s : shared) fprintf(out, "shared=%s\n", s.c_str());
    for (const auto& g : groups) fprintf(out, "%s\n", g.c_str());
    fclose(out);
}

XrResult SuggestTolerant(XrInstance instance, const XrInteractionProfileSuggestedBinding* suggested, const char* profile);

// Translates the game's bindings for [source] onto the device's primary profile.
bool EmulateFrom(XrInstance instance, const std::string& sourceProfile, const char* sourceKind, const std::vector<XrActionSuggestedBinding>& given) {
    const auto overrides = gp::ParseOverrides(getenv("GAMEPORT_XR_MAP"));
    std::string dropped;
    int droppedCount = 0;
    std::map<std::string, std::vector<std::string>> sharedMap;
    std::string tableText;  // where each control of the game went, for the log

    struct Built { XrActionSuggestedBinding binding; bool crossHand; };
    auto build = [&](bool useOverrides) {
        std::vector<Built> built;
        dropped.clear();
        droppedCount = 0;
        // The controls that compete for the device's: those the game binds.
        std::set<std::string> used;
        for (const XrActionSuggestedBinding& binding : given) {
            Control control;
            if (ParseInput(PathText(instance, binding.binding), control) && control.group != "grip" && control.group != "aim") used.insert(control.hand + ":" + control.group);
        }
        const auto allocation = gp::Allocate(used, overrides, useOverrides);
        sharedMap = gp::SharedTargets(allocation);
        tableText.clear();
        for (const auto& [source, targets] : allocation) {
            tableText += " " + source + "->";
            if (targets.empty()) tableText += "nothing";
            for (size_t i = 0; i < targets.size(); i++) tableText += (i ? "+" : "") + targets[i];
        }
        for (const XrActionSuggestedBinding& binding : given) {
            const std::string source = PathText(instance, binding.binding);
            std::vector<std::pair<std::string, bool>> mapped;  // the device's paths for this one, and whether each moves to the other hand
            Control control;
            if (ParseInput(source, control)) {
                const std::string key = control.hand + ":" + control.group;
                std::vector<std::string> targets;
                if (control.group == "grip" || control.group == "aim") {
                    targets = {key};
                } else {
                    auto it = allocation.find(key);
                    if (it != allocation.end()) targets = it->second;
                }
                for (const std::string& target : targets) {
                    std::string thand, tgroup, tsub;
                    if (!gp::SplitKey(target, thand, tgroup) || !DeviceHasGroup(thand, tgroup) || !DeviceSub(tgroup, control.sub, tsub)) continue;
                    mapped.push_back({"/user/hand/" + thand + "/input/" + tgroup + (tsub.empty() ? "" : "/" + tsub), thand != control.hand});
                }
            } else if (source == "/user/hand/left/output/haptic" || source == "/user/hand/right/output/haptic") {
                mapped.push_back({source, false});
            }
            bool any = false;
            for (const auto& [path, cross] : mapped) {
                XrPath mappedPath = XR_NULL_PATH;
                if (XR_FAILED(g_stringToPath(instance, path.c_str(), &mappedPath))) continue;
                any = true;
                bool duplicate = false;
                for (const auto& existing : built) if (existing.binding.action == binding.action && existing.binding.binding == mappedPath) duplicate = true;
                if (!duplicate) built.push_back({{binding.action, mappedPath}, cross});
            }
            if (!any && droppedCount++ < 24) dropped += " " + source;
        }
        return built;
    };

    XrPath target = XR_NULL_PATH;
    g_stringToPath(instance, g_family == Family::Pico ? kPicoProfiles[0] : "/interaction_profiles/oculus/touch_controller", &target);
    XrInteractionProfileSuggestedBinding remapped{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
    remapped.interactionProfile = target;

    // With everything the player and the defaults ask for; if the runtime refuses (an action limited to one
    // hand cannot take a control of the other), without the moves between hands; last, the plain defaults.
    XrResult result = XR_ERROR_VALIDATION_FAILURE;
    for (int attempt = 0; attempt < 3 && XR_FAILED(result); attempt++) {
        std::vector<Built> built = build(attempt < 2);
        std::vector<XrActionSuggestedBinding> bindings;
        for (const auto& item : built) if (attempt != 1 || !item.crossHand) bindings.push_back(item.binding);
        remapped.countSuggestedBindings = (uint32_t)bindings.size();
        remapped.suggestedBindings = bindings.data();
        // One action the runtime no longer knows is enough for it to refuse the whole table (-12): the others are given, as for the game's own profile.
        result = SuggestTolerant(instance, &remapped, g_family == Family::Pico ? kPicoProfiles[0] : "/interaction_profiles/oculus/touch_controller");
        LOGI("%s controls translated %s: %u binding(s) for the device (%u given, %d without an equivalent:%s) -> %d", sourceKind,
             attempt == 0 ? "with the overrides" : attempt == 1 ? "without moves between hands" : "with the defaults only",
             (unsigned)bindings.size(), (unsigned)given.size(), droppedCount, dropped.c_str(), (int)result);
    }
    LOGI("%s controls, where each went:%s", sourceKind, tableText.c_str());
    // What each control is for, by the name the game gave its action (so a report says which control opens a menu).
    {
        std::lock_guard<std::mutex> lock(g_actionNamesMutex);
        int shown = 0;
        for (const XrActionSuggestedBinding& binding : given) {
            if (shown++ >= 80) break;
            auto name = g_actionNames.find((uint64_t)binding.action);
            LOGI("%s action '%s' is bound to %s", sourceKind, name == g_actionNames.end() ? "?" : name->second.c_str(), PathText(instance, binding.binding).c_str());
        }
    }
    std::vector<std::string> shared;
    for (const auto& [device, sources] : sharedMap) {
        std::string line;
        for (const auto& s : sources) line += " " + s;
        LOGI("the device's %s takes %zu controls of the game, so its actions fire together:%s", device.c_str(), sources.size(), line.c_str());
        shared.push_back(device);
    }
    static bool reported = false;
    if (!reported) {
        reported = true;
        WriteDetectedControls(sourceKind, ControlsOf(instance, given), shared);
    }
    if (XR_SUCCEEDED(result)) {
        // The game is told the profile with the very handle it used to suggest its bindings: it compares handles, and a name read back or guessed may not be
        // the one it made (the runtime cannot always give it back).
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            auto known = g_profileHandles.find(sourceProfile);
            if (known != g_profileHandles.end()) g_emulatedSourcePath = known->second;
            else g_stringToPath(instance, sourceProfile.c_str(), &g_emulatedSourcePath);
        }
        g_emulating.store(true);
        g_usedKind = sourceKind;
        g_usedSource = sourceProfile;
    }
    return XR_SUCCEEDED(result);
}

// A runtime may refuse a whole table for the sake of one binding (an action it no longer knows, a path the profile does not have), and the game is then left
// with no control at all. The bindings are tried one at a time, to find which it refuses, and the others are given together. Only runs after a refusal.
XrResult SuggestTolerant(XrInstance instance, const XrInteractionProfileSuggestedBinding* suggested, const char* profile) {
    XrResult whole = g_suggestBindings(instance, suggested);
    // "Path unsupported" for the whole table of a profile the device does not have is left as it is (the table is translated later, see below). For the device's own
    // profile it means one path of the table is not one the profile has (Pavlov binds a thumbrest force the Touch controllers do not report): the others are given.
    const bool ownProfile = profile != nullptr && IsNativeProfile(profile);
    if (XR_SUCCEEDED(whole) || (whole == XR_ERROR_PATH_UNSUPPORTED && !ownProfile) || !suggested || suggested->countSuggestedBindings < 2) return whole;
    std::vector<XrActionSuggestedBinding> accepted;
    std::vector<std::string> refused;
    size_t mended = 0;
    for (uint32_t i = 0; i < suggested->countSuggestedBindings; i++) {
        XrInteractionProfileSuggestedBinding one = *suggested;
        one.countSuggestedBindings = 1;
        one.suggestedBindings = &suggested->suggestedBindings[i];
        if (XR_SUCCEEDED(g_suggestBindings(instance, &one))) {
            accepted.push_back(suggested->suggestedBindings[i]);
            continue;
        }
        const std::string name = PathText(instance, suggested->suggestedBindings[i].binding);
        // A trigger or a squeeze bound without saying which of its values is the game's slip: it means the analog value.
        auto endsWith = [&name](const char* tail) { const size_t n = strlen(tail); return name.size() > n && name.compare(name.size() - n, n, tail) == 0; };
        if (g_stringToPath && (endsWith("/input/trigger") || endsWith("/input/squeeze"))) {
            XrActionSuggestedBinding fixed = suggested->suggestedBindings[i];
            one.suggestedBindings = &fixed;
            if (XR_SUCCEEDED(g_stringToPath(instance, (name + "/value").c_str(), &fixed.binding)) && XR_SUCCEEDED(g_suggestBindings(instance, &one))) {
                accepted.push_back(fixed);
                mended++;
                LOGI("%s has no value named: given as %s/value", name.c_str(), name.c_str());
                continue;
            }
        }
        refused.push_back(name);
    }
    // Nothing accepted alone, or nothing refused or mended: there is no other table to give than the one that was refused.
    if (accepted.empty() || (refused.empty() && mended == 0)) return whole;
    XrInteractionProfileSuggestedBinding rest = *suggested;
    rest.countSuggestedBindings = (uint32_t)accepted.size();
    rest.suggestedBindings = accepted.data();
    const XrResult result = g_suggestBindings(instance, &rest);
    std::string names, essential, plus, dispensable;
    for (const std::string& name : refused) {
        names += " " + name;
        std::string& tier = gp::ClassifyControl(name) == gp::ControlTier::Essential ? essential : gp::ClassifyControl(name) == gp::ControlTier::EssentialPlus ? plus : dispensable;
        tier += " " + gp::ShortControlName(name);
    }
    LOGI("the runtime refused the %u bindings of %s (%d); %zu of them are refused one by one:%s, %zu mended; the %zu left are given: %d", suggested->countSuggestedBindings,
         profile, (int)whole, refused.size(), names.c_str(), mended, accepted.size(), (int)result);
    // Which of the controls left out matter: the essential ones are those of a Quest 3 or a Pico 4, "plus" those of the Quest 3's Touch Plus profile.
    LOGI("controls left out of %s: essential [%s ] | essential plus [%s ] | dispensable [%s ]", profile, essential.c_str(), plus.c_str(), dispensable.c_str());
    return result;
}

// The game's own bindings for the device's controllers, given to the runtime after all (they were kept back, see Layer_xrSuggestInteractionProfileBindings).
void ReplayDeviceProfiles(XrInstance instance, const std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>>& stash) {
    for (const auto& [profile, bindings] : stash) {
        if (!IsNativeProfile(profile) || bindings.empty()) continue;
        XrInteractionProfileSuggestedBinding suggestion{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
        if (XR_FAILED(g_stringToPath(instance, profile.c_str(), &suggestion.interactionProfile))) continue;
        suggestion.countSuggestedBindings = (uint32_t)bindings.size();
        suggestion.suggestedBindings = bindings.data();
        LOGI("kept-back bindings for %s given to the runtime: %u -> %d", profile.c_str(), suggestion.countSuggestedBindings, (int)SuggestTolerant(instance, &suggestion, profile.c_str()));
    }
}

// A line that is logged once per process, whatever the number of times the game attaches its actions.
void LogOnce(const std::string& text) {
    static std::set<std::string> said;
    if (said.insert(text).second) LOGI("%s", text.c_str());
}

// The richest suggestion of the profiles [profiles] among the game's, or "".
std::string RichestOf(const std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>>& stash, const char* const* profiles, size_t count) {
    std::string best;
    size_t size = 0;
    for (size_t i = 0; i < count; i++) {
        auto it = stash.find(profiles[i]);
        if (it != stash.end() && it->second.size() > size) { best = profiles[i]; size = it->second.size(); }
    }
    return best;
}

// The player chose the Touch controls (GAMEPORT_XR_PRESENT=touch). Only a Pico gives them a meaning: its runtime has no table of its own for a game made for the Meta's
// (nor for the Steam Frame's), so the Touch's is translated before the Steam Frame's gets its turn. True when that was done; false leaves the automatic path to go on,
// with whatever was kept back given to the runtime again if the translation failed.
bool PresentedFirst(XrInstance instance, const std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>>& stash, const std::string& native) {
    if (g_present.load() != static_cast<int>(Present::Touch)) return false;  // "frame" goes through the existing preference (g_preferFrame)
    if (g_family != Family::Pico) {
        LogOnce("the player chose the Touch controls: this device has no use for that choice, it is ignored");
        return false;
    }
    if (!native.empty()) {
        LogOnce("the player chose the Touch controls, but the game has its own for this device: the choice is ignored");
        return false;
    }
    const std::string touch = RichestOf(stash, kMetaProfiles, sizeof(kMetaProfiles) / sizeof(*kMetaProfiles));
    if (touch.empty()) {
        LogOnce("the player chose the Touch controls, but the game has no table for them: the automatic choice is used");
        return false;
    }
    if (EmulateFrom(instance, touch, "touch", stash.at(touch))) {
        LogOnce("the game uses the Touch controls, as the player asked");
        return true;
    }
    LogOnce("the Touch controls could not be translated: the game's tables are given back to the runtime and the automatic choice is used");
    ReplayDeviceProfiles(instance, stash);
    return false;
}

// Runs once the game has said everything it knows, right before it attaches its action sets.
void EmulateCore(XrInstance instance) {
    if (g_family == Family::Unknown || !g_stringToPath || !g_pathToString) return;
    std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>> stash;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        stash = g_stash;
    }
    // The device's own profile the game has the most bindings for, and the Steam Frame's.
    std::string native, valve;
    for (const auto& [profile, bindings] : stash) {
        if (IsNativeProfile(profile) && (native.empty() || bindings.size() > stash[native].size())) native = profile;
        if (IsValveProfile(profile) && (valve.empty() || bindings.size() > stash[valve].size())) valve = profile;
    }
    // The controllers the player chose to present (not "automatic"): decided first, the rest below stays as it was.
    if (g_present.load() != static_cast<int>(Present::Auto) && PresentedFirst(instance, stash, native)) return;
    if (!native.empty()) {
        g_emulating.store(false);
        // The game has controls for the device's controllers: nothing to translate. When it also has the Steam Frame's, the log says how the two compare. Unity
        // and Unreal make their own actions for each profile (a few shared ones aside), so the Steam Frame's are not missing on the device's: they are
        // read when that profile is the active one.
        if (!valve.empty()) {
            // The player may choose the Steam Frame's controls for this game: the page is offered, and the choice is read from GAMEPORT_XR_PREFER_FRAME.
            g_gameHasBoth.store(true);
            static bool offered = false;
            if (!offered) {
                offered = true;
                WriteDetectedControls("valve", ControlsOf(instance, stash[valve]), {});
            }
            std::set<XrAction> frameActions;
            for (const auto& binding : stash[valve]) frameActions.insert(binding.action);
            std::vector<std::set<XrAction>> deviceTables;
            for (const auto& [profile, bindings] : stash) {
                if (!IsNativeProfile(profile)) continue;
                deviceTables.emplace_back();
                for (const auto& binding : bindings) deviceTables.back().insert(binding.action);
            }
            const gp::Gaps<XrAction> gaps = gp::FindGaps(frameActions, deviceTables);
            LOGI("the game has bindings for %zu profile(s) of this device's controllers (the richest: %s) and for the Steam Frame's (%s: %zu actions): %zu actions in common, the others are its own to that profile: nothing to translate",
                 deviceTables.size(), native.c_str(), valve.c_str(), frameActions.size(), gaps.shared);
        } else {
            LOGI("the game has its own bindings for this device's controllers (%s): nothing to translate", native.c_str());
        }
        if (!valve.empty() && g_preferFrame.load() && EmulateFrom(instance, valve, "valve", stash[valve])) {
            LOGI("the game uses the Steam Frame's controls, as the player asked");
            return;
        }
        if (valve.empty()) {
            static bool reported = false;
            if (!reported) {
                reported = true;
                WriteDetectedControls("none", {}, {});
            }
        }
        // What was kept back for the Steam Frame's controls goes to the runtime after all.
        if (g_preferFrame.load()) ReplayDeviceProfiles(instance, stash);
        return;
    }
    // The device's own family first, then the Steam Frame's. The richest suggestion of a family is the source.
    auto richest = [&](const char* const* profiles, size_t count) -> std::string {
        std::string best;
        size_t size = 0;
        for (size_t i = 0; i < count; i++) {
            auto it = stash.find(profiles[i]);
            if (it != stash.end() && it->second.size() > size) { best = profiles[i]; size = it->second.size(); }
        }
        return best;
    };
    if (g_family == Family::Pico) {
        // The game has the Steam Frame's controls: they are the ones read. A game made for the Steam Frame makes its own actions for that profile (Moss), which a table
        // translated from the Touch's would leave without a binding, and the player would find the game deaf to the controllers. Nothing is detected or asked: a Pico has
        // no profile the game knows, so both tables have to be translated and the one the game was made for goes first. The Touch's is the fallback.
        if (!valve.empty() && EmulateFrom(instance, valve, "valve", stash[valve])) {
            LOGI("the game has the Steam Frame's controls: they are translated onto this device's controllers%s", g_preferFrame.load() ? ", as the player asked" : "");
            return;
        }
        const std::string touch = richest(kMetaProfiles, sizeof(kMetaProfiles) / sizeof(*kMetaProfiles));
        if (!touch.empty() && EmulateFrom(instance, touch, "touch", stash[touch])) return;
    }
    if (!valve.empty()) EmulateFrom(instance, valve, "valve", stash[valve]);
}

// What became of the player's choice: "applied" (also said when there was no choice to make), "fallback" (the game has what was asked for, but it could not be
// used) or "ignored" (the choice has no meaning for this device or this game).
const char* PresentedOutcome(Present present, Family family, bool native, bool valve, const std::string& used) {
    // A device whose family is unknown has nothing translated at all: the choice has no meaning there.
    if (present == Present::Frame) return !valve || family == Family::Unknown ? "ignored" : used == "frame" ? "applied" : "fallback";
    if (present == Present::Touch) return family != Family::Pico || native ? "ignored" : used == "touch" ? "applied" : "fallback";
    return "applied";
}

// A text that goes in a line of the report: no separator of the format, no control character, a sane length.
bool ReportableProfile(const std::string& name) {
    if (name.empty() || name.size() > 150) return false;
    for (unsigned char c : name) if (c < 0x21 || c > 0x7e || c == '|' || c == ':') return false;
    return true;
}

// The lines of xr_profiles.txt: every profile the game gave bindings for, what the player chose, what became of it and which table is in use.
std::string ProfilesFileText(const std::vector<std::pair<std::string, size_t>>& profiles, Present present, const char* presented, const char* used) {
    std::string text;
    int shown = 0;
    for (const auto& [name, count] : profiles) {
        if (shown++ >= 16) break;
        text += "profile=" + name + "|" + std::to_string(count) + "\n";
    }
    text += std::string("present=") + PresentName(present) + "\n";
    text += std::string("presented=") + presented + "\n";
    text += std::string("used=") + used + "\n";
    return text;
}

// Test seam: where the text goes instead of the file, when set.
void (*g_writeProfilesText)(const std::string&) = nullptr;

// Written whole or not at all: a temporary file in the same folder, then renamed over the real one, so that a reader never sees half a file.
bool WriteFileAtomically(const std::string& path, const std::string& text) {
    const std::string temporary = path + ".tmp";
    FILE* out = fopen(temporary.c_str(), "w");
    if (!out) return false;
    const bool written = fputs(text.c_str(), out) >= 0;
    const bool closed = fclose(out) == 0;
    if (!written || !closed || rename(temporary.c_str(), path.c_str()) != 0) {
        remove(temporary.c_str());
        return false;
    }
    return true;
}

// A new run starts without the report of the last one: a hook that reads it before this run wrote its own must not take an old answer for the new choice.
void RemoveStaleProfilesText(const std::string& folder) {
    if (!folder.empty()) remove((folder + "xr_profiles.txt").c_str());
}

void WriteProfilesText(const std::string& text) {
    if (g_writeProfilesText) {
        g_writeProfilesText(text);
        return;
    }
    const std::string folder = GamePortFolder();
    if (folder.empty()) return;
    WriteFileAtomically(folder + "xr_profiles.txt", text);
}

// Tells GamePort (a file of its own, next to xr_controls.txt, which is left as it was) which profiles the game gave bindings for, what the player chose and what became of it,
// and which table is used. The log gets a line for each table that was not translated (the translated one has its own, per action). Written again only when it changed.
void ReportProfiles() {
    std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>> stash;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        stash = g_stash;
    }
    std::vector<std::pair<std::string, size_t>> profiles;
    bool native = false, valve = false;
    for (const auto& [profile, bindings] : stash) {
        if (IsNativeProfile(profile)) native = true;
        if (IsValveProfile(profile)) valve = true;
        if (!ReportableProfile(profile)) continue;
        profiles.push_back({profile, bindings.size()});
        if (profile != g_usedSource) {
            std::set<XrAction> distinct;
            for (const auto& binding : bindings) distinct.insert(binding.action);
            LogOnce("controller profile: " + profile + ", " + std::to_string(bindings.size()) + " binding(s), " + std::to_string(distinct.size()) + " distinct action(s)");
        }
    }
    std::sort(profiles.begin(), profiles.end());
    const std::string used = g_usedKind == "valve" ? "frame" : !g_usedKind.empty() ? g_usedKind : native ? "native" : "none";
    const Present present = static_cast<Present>(g_present.load());
    const char* outcome = PresentedOutcome(present, g_family, native, valve, used);
    const std::string text = ProfilesFileText(profiles, present, outcome, used.c_str());
    LogOnce(std::string("controller profile: present=") + PresentName(present) + " presented=" + outcome + " used=" + used);
    static std::string written;
    if (text == written) return;
    written = text;
    WriteProfilesText(text);
}

// Runs once the game has said everything it knows, right before it attaches its action sets, then reports what the game has and what was done.
void EmulateIfNeeded(XrInstance instance) {
    g_usedKind.clear();
    g_usedSource.clear();
    EmulateCore(instance);
    ReportProfiles();
}

// The calls that end a session or an instance: logged, so that a report shows who closed what (the game by itself, or the runtime).
XrResult XRAPI_CALL Layer_xrEndSession(XrSession session) {
    XrResult result = g_endSession(session);
    LOGI("xrEndSession -> %d", (int)result);
    return result;
}

XrResult XRAPI_CALL Layer_xrRequestExitSession(XrSession session) {
    LOGI("the game asks the runtime to end the session (xrRequestExitSession)");
    return g_requestExit(session);
}

XrResult XRAPI_CALL Layer_xrDestroySession(XrSession session) {
    LOGI("xrDestroySession");
    g_passthrough = XR_NULL_HANDLE;
    g_passthroughLayer = XR_NULL_HANDLE;
    g_passthroughState.store(0);
    return g_destroySession(session);
}

XrResult XRAPI_CALL Layer_xrDestroyInstance(XrInstance instance) {
    LOGI("xrDestroyInstance");
    return g_destroyInstance(instance);
}

XrResult XRAPI_CALL Layer_xrCreateAction(XrActionSet actionSet, const XrActionCreateInfo* createInfo, XrAction* action) {
    XrResult result = g_createAction ? g_createAction(actionSet, createInfo, action) : XR_ERROR_FUNCTION_UNSUPPORTED;
    if (XR_SUCCEEDED(result) && createInfo && action) {
        std::lock_guard<std::mutex> lock(g_actionNamesMutex);
        if (g_actionNames.size() < 2000) g_actionNames[(uint64_t)*action] = createInfo->actionName;
    }
    return result;
}

XrResult XRAPI_CALL Layer_xrSuggestInteractionProfileBindings(XrInstance instance, const XrInteractionProfileSuggestedBinding* suggested) {
    // The button recorder is a test run: the game's own bindings are left out, so its profile does not replace the recorder's.
    if (RecorderOn()) return XR_SUCCESS;
    std::string profile;
    size_t actions = 0;
    if (suggested && g_pathToString) {
        profile = PathText(instance, suggested->interactionProfile);
        // A runtime that does not know a profile may not give its name back (Unreal games ask for the Steam Frame's that way): its controls tell.
        if (profile.empty()) {
            for (uint32_t i = 0; i < suggested->countSuggestedBindings; i++) {
                if (gp::IsFrameControlPath(PathText(instance, suggested->suggestedBindings[i].binding))) {
                    profile = "/interaction_profiles/valve/frame_controller_valve";
                    LOGI("a profile without a name binds controls only the Steam Frame's controllers have: taken for %s", profile.c_str());
                    break;
                }
            }
        }
        std::set<XrAction> distinct;
        for (uint32_t i = 0; i < suggested->countSuggestedBindings; i++) distinct.insert(suggested->suggestedBindings[i].action);
        actions = distinct.size();
        std::lock_guard<std::mutex> lock(g_mutex);
        g_stash[profile].assign(suggested->suggestedBindings, suggested->suggestedBindings + suggested->countSuggestedBindings);
        g_profileHandles[profile] = suggested->interactionProfile;
    }
    // When the player wants the Steam Frame's controls, the game's own tables for the device's controllers are kept back until it attaches its actions:
    // if it has the Steam Frame's, these are then given to the runtime on the device's profile, which is the only one it knows, so it is the one in use.
    if (suggested && g_preferFrame.load() && !profile.empty() && IsNativeProfile(profile)) {
        LOGI("xrSuggestInteractionProfileBindings(%s, %u bindings, %zu actions) -> kept back", profile.c_str(), suggested->countSuggestedBindings, actions);
        return XR_SUCCESS;
    }
    // Pico's runtime does not answer "path unsupported" for the Steam Frame's profile, which it does not know: it crashes inside itself (a null name copied) at the
    // first table it is given for it (Moss, Metro). That table is kept, as for any profile the device does not have, and translated onto the device's own controllers
    // when the game attaches its actions.
    if (suggested && g_family == Family::Pico && IsValveProfile(profile)) {
        LOGI("xrSuggestInteractionProfileBindings(%s, %u bindings, %zu actions) -> kept for the translation: this runtime does not know that profile", profile.c_str(),
             suggested->countSuggestedBindings, actions);
        return XR_SUCCESS;
    }
    if (suggested) {
        // Said before the call, so that a crash inside the runtime leaves the name of the profile in the log.
        LOGI("xrSuggestInteractionProfileBindings(%s, %u bindings, %zu actions): handed to the runtime", profile.empty() ? "<unnamed profile>" : profile.c_str(),
             suggested->countSuggestedBindings, actions);
    }
    XrResult result = SuggestTolerant(instance, suggested, profile.c_str());
    if (suggested) {
        LOGI("xrSuggestInteractionProfileBindings(%s, %u bindings, %zu actions) -> %d", profile.empty() ? "<unnamed profile>" : profile.c_str(),
             suggested->countSuggestedBindings, actions, (int)result);
    }
    // A profile the device does not have is not an error worth failing the game over: it may be translated later.
    if (result == XR_ERROR_PATH_UNSUPPORTED && g_family != Family::Unknown && !IsNativeProfile(profile)) return XR_SUCCESS;
    return result;
}

// Remembers the names of the interaction profiles the game creates, in case the runtime does not give them back.
XrResult XRAPI_CALL Layer_xrStringToPath(XrInstance instance, const char* pathString, XrPath* path) {
    if (!g_stringToPath) return XR_ERROR_HANDLE_INVALID;
    XrResult result = g_stringToPath(instance, pathString, path);
    if (XR_SUCCEEDED(result) && pathString && path && strncmp(pathString, "/interaction_profiles/", 22) == 0) {
        std::lock_guard<std::mutex> lock(g_namesMutex);
        g_pathNames[*path] = pathString;
    }
    return result;
}

// Only watches: the runtime announces a recentering with an event, and what the game does with it is the game's. The first ones are written in the log.
XrResult XRAPI_CALL Layer_xrPollEvent(XrInstance instance, XrEventDataBuffer* data) {
    XrResult result = g_pollEvent(instance, data);
    // Every kind of event the game is sent, the first three times it comes (the ones below say more about themselves).
    if (result == XR_SUCCESS && data) {
        static std::mutex seenMutex;
        static std::unordered_map<int, int> seen;
        std::lock_guard<std::mutex> lock(seenMutex);
        if (++seen[(int)data->type] <= 3) LOGI("event of type %d received (time %d)", (int)data->type, seen[(int)data->type]);
    }
    // The states of the session, in order: what the game was told, and when, tells where a game that quits or never shows an image stopped.
    if (result == XR_SUCCESS && data && data->type == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
        const auto* change = reinterpret_cast<const XrEventDataSessionStateChanged*>(data);
        LOGI("session state -> %d (1 idle, 2 ready, 3 synchronized, 4 visible, 5 focused, 6 stopping, 7 loss pending, 8 exiting)", (int)change->state);
    } else if (result == XR_SUCCESS && data && data->type == XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING) {
        LOGI("the runtime announces that the instance will be lost");
    } else if (result == XR_SUCCESS && data && data->type == XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED) {
        LOGI("the runtime announces a change of the interaction profile in use");
    }
    if (result == XR_SUCCESS && data && data->type == XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING) {
        const auto* change = reinterpret_cast<const XrEventDataReferenceSpaceChangePending*>(data);
        static std::atomic<int> logged{0};
        if (logged.fetch_add(1) < 20) {
            LOGI("the runtime announces a change of the reference space (type %d, new pose %s): the game is to recenter on it", (int)change->referenceSpaceType,
                 change->poseValid ? "given" : "not given");
        }
    }
    return result;
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------------
// Button recorder: a test that tells which control of the device's controllers answers to each button, whatever the game binds. The file xr_record_buttons.txt in the
// game's folder turns it on. The game's own bindings are then left out (it is a test run: the game hears nothing), and the layer makes its own actions for every
// control of the Pico 4 controller (and the simple profile), attaches them with the game's sets, and writes a line "REC <path> = <value>" at each change.
// ---------------------------------------------------------------------------------------------------------------------------------------------------------
bool RecorderOn() {
    static int on = -1;
    if (on < 0) {
        FILE* file = fopen((GamePortFolder() + "xr_record_buttons.txt").c_str(), "r");
        on = file ? 1 : 0;
        if (file) fclose(file);
    }
    return on == 1;
}

struct RecInput {
    std::string path;
    int kind;  // 0 on/off, 1 a value, 2 a stick (two values)
    XrAction action = XR_NULL_HANDLE;
    float last[2] = {0, 0};
    bool seen = false;
};
std::vector<RecInput> g_rec;
XrActionSet g_recSet = XR_NULL_HANDLE;
std::atomic<bool> g_recReady{false};

void SetUpRecorder(XrInstance instance) {
    if (!g_createActionSet || !g_createAction || !g_stringToPath || !g_suggestBindings) {
        LOGI("REC cannot start: the runtime lacks a function the recorder needs");
        return;
    }
    XrActionSetCreateInfo setInfo{XR_TYPE_ACTION_SET_CREATE_INFO};
    strcpy(setInfo.actionSetName, "gpbuttons");
    strcpy(setInfo.localizedActionSetName, "GamePort buttons");
    if (XR_FAILED(g_createActionSet(instance, &setInfo, &g_recSet))) {
        LOGI("REC cannot start: the action set was refused");
        return;
    }
    const char* hands[2] = {"left", "right"};
    struct Control { const char* name; int kind; bool left; bool right; };
    const Control controls[] = {
        {"x/click", 0, true, false}, {"x/touch", 0, true, false}, {"y/click", 0, true, false}, {"y/touch", 0, true, false}, {"menu/click", 0, true, false},
        {"a/click", 0, false, true}, {"a/touch", 0, false, true}, {"b/click", 0, false, true}, {"b/touch", 0, false, true}, {"system/click", 0, false, true},
        {"squeeze/click", 0, true, true}, {"squeeze/value", 1, true, true}, {"trigger/click", 0, true, true}, {"trigger/value", 1, true, true}, {"trigger/touch", 0, true, true},
        {"thumbstick", 2, true, true}, {"thumbstick/click", 0, true, true}, {"thumbstick/touch", 0, true, true}, {"thumbrest/touch", 0, true, true},
    };
    for (int hand = 0; hand < 2; hand++) {
        for (const Control& control : controls) {
            if ((hand == 0 && !control.left) || (hand == 1 && !control.right)) continue;
            RecInput input;
            input.path = std::string("/user/hand/") + hands[hand] + "/input/" + control.name;
            input.kind = control.kind;
            XrActionCreateInfo info{XR_TYPE_ACTION_CREATE_INFO};
            snprintf(info.actionName, sizeof(info.actionName), "r%02d", (int)g_rec.size());
            snprintf(info.localizedActionName, sizeof(info.localizedActionName), "%s", input.path.c_str());
            info.actionType = control.kind == 0 ? XR_ACTION_TYPE_BOOLEAN_INPUT : control.kind == 1 ? XR_ACTION_TYPE_FLOAT_INPUT : XR_ACTION_TYPE_VECTOR2F_INPUT;
            if (XR_FAILED(g_createAction(g_recSet, &info, &input.action))) continue;
            g_rec.push_back(input);
        }
    }
    // Bindings on the Pico 4 controller's profile, and the simple one (its menu and select), each refused path left out by SuggestTolerant.
    const char* profiles[] = {"/interaction_profiles/bytedance/pico4_controller", "/interaction_profiles/khr/simple_controller"};
    for (const char* profile : profiles) {
        std::vector<XrActionSuggestedBinding> bindings;
        for (const RecInput& input : g_rec) {
            const bool simple = strstr(profile, "simple") != nullptr;
            if (simple && input.path.find("/input/menu/click") == std::string::npos && input.path.find("/input/trigger/click") == std::string::npos) continue;
            // The simple profile calls the trigger "select".
            std::string path = input.path;
            if (simple && path.find("/input/trigger/click") != std::string::npos) path.replace(path.find("trigger/click"), 13, "select/click");
            XrActionSuggestedBinding binding{input.action, XR_NULL_PATH};
            if (XR_FAILED(g_stringToPath(instance, path.c_str(), &binding.binding))) continue;
            bindings.push_back(binding);
        }
        XrInteractionProfileSuggestedBinding suggestion{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
        if (XR_FAILED(g_stringToPath(instance, profile, &suggestion.interactionProfile))) continue;
        suggestion.countSuggestedBindings = (uint32_t)bindings.size();
        suggestion.suggestedBindings = bindings.data();
        LOGI("REC bindings on %s: %u asked -> %d", profile, suggestion.countSuggestedBindings, (int)SuggestTolerant(instance, &suggestion, profile));
    }
}

XrResult XRAPI_CALL Layer_xrAttachSessionActionSets(XrSession session, const XrSessionActionSetsAttachInfo* info) {
    if (RecorderOn() && info) {
        SetUpRecorder(g_instance);
        std::vector<XrActionSet> sets(info->actionSets, info->actionSets + info->countActionSets);
        if (g_recSet != XR_NULL_HANDLE) sets.push_back(g_recSet);
        XrSessionActionSetsAttachInfo more = *info;
        more.countActionSets = (uint32_t)sets.size();
        more.actionSets = sets.data();
        const XrResult result = g_attachActionSets(session, &more);
        g_recReady.store(XR_SUCCEEDED(result) && g_recSet != XR_NULL_HANDLE);
        LOGI("REC attached %zu inputs with the game's %u sets -> %d", g_rec.size(), info->countActionSets, (int)result);
        return result;
    }
    EmulateIfNeeded(g_instance);
    return g_attachActionSets(session, info);
}

XrResult XRAPI_CALL Layer_xrSyncActions(XrSession session, const XrActionsSyncInfo* info) {
    if (!g_recReady.load() || !info) return g_syncActions(session, info);
    std::vector<XrActiveActionSet> sets(info->activeActionSets, info->activeActionSets + info->countActiveActionSets);
    sets.push_back(XrActiveActionSet{g_recSet, XR_NULL_PATH});
    XrActionsSyncInfo more = *info;
    more.countActiveActionSets = (uint32_t)sets.size();
    more.activeActionSets = sets.data();
    const XrResult result = g_syncActions(session, &more);
    if (XR_FAILED(result)) return result;
    for (RecInput& input : g_rec) {
        XrActionStateGetInfo get{XR_TYPE_ACTION_STATE_GET_INFO, nullptr, input.action, XR_NULL_PATH};
        float now[2] = {0, 0};
        bool active = false;
        if (input.kind == 0 && g_getActionBoolean) {
            XrActionStateBoolean state{XR_TYPE_ACTION_STATE_BOOLEAN};
            if (XR_SUCCEEDED(g_getActionBoolean(session, &get, &state))) { active = state.isActive == XR_TRUE; now[0] = state.currentState ? 1.f : 0.f; }
        } else if (input.kind == 1 && g_getActionFloat) {
            XrActionStateFloat state{XR_TYPE_ACTION_STATE_FLOAT};
            if (XR_SUCCEEDED(g_getActionFloat(session, &get, &state))) { active = state.isActive == XR_TRUE; now[0] = state.currentState; }
        } else if (input.kind == 2 && g_getActionVector2) {
            XrActionStateVector2f state{XR_TYPE_ACTION_STATE_VECTOR2F};
            if (XR_SUCCEEDED(g_getActionVector2(session, &get, &state))) { active = state.isActive == XR_TRUE; now[0] = state.currentState.x; now[1] = state.currentState.y; }
        }
        if (!input.seen) {
            input.seen = true;
            LOGI("REC %s is %s", input.path.c_str(), active ? "bound" : "not bound");
        }
        if (!active) continue;
        const bool changed = input.kind == 0 ? now[0] != input.last[0] : (fabsf(now[0] - input.last[0]) > 0.25f || fabsf(now[1] - input.last[1]) > 0.25f);
        if (!changed) continue;
        input.last[0] = now[0];
        input.last[1] = now[1];
        if (input.kind == 2) LOGI("REC %s = %.2f, %.2f", input.path.c_str(), now[0], now[1]);
        else LOGI("REC %s = %.2f", input.path.c_str(), now[0]);
    }
    return result;
}

// While a translation is on, the game is told that the profile it knows is the one in use.
XrResult XRAPI_CALL Layer_xrGetCurrentInteractionProfile(XrSession session, XrPath userPath, XrInteractionProfileState* state) {
    XrResult result = g_getCurrentProfile(session, userPath, state);
    static std::atomic<int> logged{0};
    const bool log = logged.fetch_add(1) < 8;
    if (XR_SUCCEEDED(result) && state && g_emulating.load() && state->interactionProfile != XR_NULL_PATH) {
        const std::string actual = PathText(g_instance, state->interactionProfile);
        const bool replaced = IsNativeProfile(actual);
        if (replaced) state->interactionProfile = g_emulatedSourcePath;
        if (log) LOGI("the game asks which controllers are in use (%s): the runtime says %s, the game is told %s", PathText(g_instance, userPath).c_str(), actual.c_str(), replaced ? "the Steam Frame's" : "the same");
    } else if (log && state) {
        LOGI("the game asks which controllers are in use (%s): %s", PathText(g_instance, userPath).c_str(), g_emulating.load() ? "no profile yet" : "left as the runtime says");
    }
    return result;
}

// A vibration the game asks for, at the strength the player chose for this game. Only the amplitude of a plain vibration changes; any other kind of haptic goes through as it is.
XrResult XRAPI_CALL Layer_xrApplyHapticFeedback(XrSession session, const XrHapticActionInfo* actionInfo, const XrHapticBaseHeader* haptic) {
    if (!g_applyHaptic) return XR_ERROR_FUNCTION_UNSUPPORTED;
    const int percent = g_hapticPercent.load();
    if (percent == 100 || !haptic || haptic->type != XR_TYPE_HAPTIC_VIBRATION) return g_applyHaptic(session, actionInfo, haptic);
    XrHapticVibration scaled = *reinterpret_cast<const XrHapticVibration*>(haptic);
    scaled.amplitude = gp::ScaleHapticAmplitude(scaled.amplitude, percent);
    return g_applyHaptic(session, actionInfo, reinterpret_cast<const XrHapticBaseHeader*>(&scaled));
}

// Meta's own plugins (the Unity "Meta XR" feature, OVRPlugin) switch themselves off on a runtime that is not Meta's, by its name, and the hooks they put on the game's
// OpenXR calls then call functions that were never bound. A game can be shown the name of Meta's runtime instead (the file xr_runtime_name.txt of the game's folder, one
// word): the layer's own decisions keep reading the real one.
std::string SpokenRuntimeName() {
    static std::string cached;
    static std::atomic<int> state{-1};
    int value = state.load();
    if (value < 0) {
        std::string name;
        FILE* in = fopen((GamePortFolder() + "xr_runtime_name.txt").c_str(), "r");
        if (in) {
            int c;
            while ((c = fgetc(in)) != EOF && c != '\n' && c != '\r' && name.size() < XR_MAX_RUNTIME_NAME_SIZE - 1) name += (char)c;
            fclose(in);
        }
        // Without the file: a Pico shows the name of Meta's runtime to a game that carries Meta's OVRPlugin (it is loaded already, the game loads it before OpenXR).
        const char* family = getenv("GAMEPORT_XR_FAMILY");
        std::string lowered = family ? family : "";
        for (char& ch : lowered) ch = (char)tolower((unsigned char)ch);
        if (name.empty() && lowered.find("pico") != std::string::npos && dlopen("libOVRPlugin.so", RTLD_NOLOAD | RTLD_LAZY)) name = "Oculus";
        cached = name;
        state.store(1);
        return cached;
    }
    return cached;
}

// Test file xr_system_name.txt, "name|vendor id": the game is shown another headset name and vendor (OVRPort's loader shows "Oculus Quest2", 10291).
XrResult XRAPI_CALL Layer_xrGetSystemProperties(XrInstance instance, XrSystemId systemId, XrSystemProperties* properties) {
    const XrResult result = g_getSystemProperties(instance, systemId, properties);
    if (XR_SUCCEEDED(result) && properties) {
        const std::string text = TestFileText("xr_system_name.txt");
        if (!text.empty()) {
            const size_t bar = text.find('|');
            const std::string name = text.substr(0, bar);
            const unsigned vendor = bar == std::string::npos ? properties->vendorId : (unsigned)strtoul(text.c_str() + bar + 1, nullptr, 10);
            static std::atomic<bool> told{false};
            if (!told.exchange(true)) LOGI("the headset is called '%s', vendor %u, to the game (it is '%s', vendor %u; test file)", name.c_str(), vendor, properties->systemName, properties->vendorId);
            snprintf(properties->systemName, sizeof(properties->systemName), "%s", name.c_str());
            properties->vendorId = vendor;
        }
    }
    return result;
}

// Test file xr_hide_vulkan_device_extensions.txt (names separated by spaces): these extensions are not enabled on the Vulkan device the runtime creates for the game.
XrResult XRAPI_CALL Layer_xrCreateVulkanDeviceKHR(XrInstance instance, const XrVulkanDeviceCreateInfoKHR* createInfo, VkDevice* vulkanDevice, VkResult* vulkanResult) {
    const std::string text = TestFileText("xr_hide_vulkan_device_extensions.txt");
    if (text.empty() || !createInfo || !createInfo->vulkanCreateInfo) return g_createVulkanDevice(instance, createInfo, vulkanDevice, vulkanResult);
    std::set<std::string> hidden;
    std::string word;
    for (char ch : text + " ") {
        if (ch == ' ' || ch == ',' || ch == ';') { if (!word.empty()) hidden.insert(word); word.clear(); } else word += ch;
    }
    XrVulkanDeviceCreateInfoKHR info = *createInfo;
    VkDeviceCreateInfo device = *createInfo->vulkanCreateInfo;
    std::vector<const char*> kept;
    for (uint32_t i = 0; i < device.enabledExtensionCount; i++) {
        if (hidden.count(device.ppEnabledExtensionNames[i])) {
            LOGI("vulkan device extension %s not passed on (test file)", device.ppEnabledExtensionNames[i]);
            continue;
        }
        kept.push_back(device.ppEnabledExtensionNames[i]);
    }
    device.enabledExtensionCount = (uint32_t)kept.size();
    device.ppEnabledExtensionNames = kept.data();
    info.vulkanCreateInfo = &device;
    return g_createVulkanDevice(instance, &info, vulkanDevice, vulkanResult);
}

XrResult XRAPI_CALL Layer_xrGetInstanceProperties(XrInstance instance, XrInstanceProperties* properties) {
    const XrResult result = g_getInstanceProperties(instance, properties);
    if (XR_SUCCEEDED(result) && properties) {
        const std::string spoken = SpokenRuntimeName();
        if (!spoken.empty()) {
            static std::atomic<bool> told{false};
            if (!told.exchange(true)) LOGI("the runtime is called '%s' to the game (it is '%s')", spoken.c_str(), properties->runtimeName);
            snprintf(properties->runtimeName, sizeof(properties->runtimeName), "%s", spoken.c_str());
        }
    }
    return result;
}

XrResult XRAPI_CALL Layer_xrGetInstanceProcAddr(XrInstance instance, const char* name, PFN_xrVoidFunction* function) {
    // The loader can ask before the instance exists, or after the chain was rebuilt: nothing below us yet.
    if (!g_nextGetInstanceProcAddr) {
        if (function) *function = nullptr;
        return XR_ERROR_HANDLE_INVALID;
    }
    struct Entry { const char* name; PFN_xrVoidFunction fn; };
    static const Entry entries[] = {
        {"xrCreateReferenceSpace", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrCreateReferenceSpace)},
        {"xrDestroySpace", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrDestroySpace)},
        {"xrLocateSpace", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrLocateSpace)},
        {"xrLocateSpaces", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrLocateSpaces)},
        {"xrLocateViews", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrLocateViews)},
        {"xrEndFrame", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrEndFrame)},
        {"xrEnumerateEnvironmentBlendModes", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrEnumerateEnvironmentBlendModes)},
        {"xrGetInstanceProperties", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetInstanceProperties)},
        {"xrGetSystemProperties", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetSystemProperties)},
        {"xrCreateVulkanDeviceKHR", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrCreateVulkanDeviceKHR)},
        {"xrWaitFrame", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrWaitFrame)},
        {"xrBeginSession", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrBeginSession)},
        {"xrRequestDisplayRefreshRateFB", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrRequestDisplayRefreshRateFB)},
        {"xrCreateSwapchain", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrCreateSwapchain)},
        {"xrDestroySwapchain", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrDestroySwapchain)},
        {"xrEnumerateViewConfigurationViews", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrEnumerateViewConfigurationViews)},
        {"xrEnumerateSwapchainFormats", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrEnumerateSwapchainFormats)},
        {"xrGetCurrentInteractionProfile", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetCurrentInteractionProfile)},
        {"xrAttachSessionActionSets", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrAttachSessionActionSets)},
        {"xrSyncActions", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrSyncActions)},
        {"xrSuggestInteractionProfileBindings", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrSuggestInteractionProfileBindings)},
        {"xrCreateAction", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrCreateAction)},
        {"xrEndSession", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrEndSession)},
        {"xrRequestExitSession", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrRequestExitSession)},
        {"xrDestroySession", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrDestroySession)},
        {"xrDestroyInstance", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrDestroyInstance)},
        {"xrPollEvent", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrPollEvent)},
        {"xrStringToPath", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrStringToPath)},
        {"xrGetActionStateBoolean", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetActionStateBoolean)},
        {"xrGetActionStateFloat", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetActionStateFloat)},
        {"xrApplyHapticFeedback", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrApplyHapticFeedback)},
    };
    if (name) {
        for (const Entry& entry : entries) {
            if (strcmp(name, entry.name) == 0) {
                // Only hand out a wrapper if the runtime has the function underneath it.
                PFN_xrVoidFunction below = nullptr;
                if (g_nextGetInstanceProcAddr && XR_SUCCEEDED(g_nextGetInstanceProcAddr(instance, name, &below)) && below) {
                    *function = entry.fn;
                    return XR_SUCCESS;
                }
            }
        }
    }
    return g_nextGetInstanceProcAddr(instance, name, function);
}

template <typename T>
void Load(const char* name, T& target) {
    PFN_xrVoidFunction function = nullptr;
    g_nextGetInstanceProcAddr(g_instance, name, &function);
    target = reinterpret_cast<T>(function);
}

constexpr const char* kEyeFoveation = "XR_META_foveation_eye_tracked";
constexpr const char* kEyeGaze = "XR_EXT_eye_gaze_interaction";
constexpr const char* kPicoControllers = "XR_BD_controller_interaction";
constexpr const char* kAndroidCreate = "XR_KHR_android_create_instance";

// XrInstanceCreateInfoAndroidKHR, written here so that the layer needs no platform header.
struct GpAndroidCreateInfo {
    XrStructureType type;
    const void* next;
    void* applicationVM;
    void* applicationActivity;
};
constexpr XrStructureType kAndroidCreateType = (XrStructureType)1000008000;

bool HasExtension(const std::vector<XrExtensionProperties>& list, const char* name) {
    for (const auto& e : list) if (strcmp(e.extensionName, name) == 0) return true;
    return false;
}

// Whether the runtime lists an extension among those it offers.
bool RuntimeOffers(XrApiLayerNextInfo* next, const char* name) {
    PFN_xrGetInstanceProcAddr getProc = next->nextGetInstanceProcAddr;
    PFN_xrEnumerateInstanceExtensionProperties enumerate = nullptr;
    if (!getProc || XR_FAILED(getProc(XR_NULL_HANDLE, "xrEnumerateInstanceExtensionProperties", (PFN_xrVoidFunction*)&enumerate)) || !enumerate) return false;
    uint32_t count = 0;
    if (XR_FAILED(enumerate(nullptr, 0, &count, nullptr)) || count == 0) return false;
    std::vector<XrExtensionProperties> offered(count, {XR_TYPE_EXTENSION_PROPERTIES});
    if (XR_FAILED(enumerate(nullptr, count, &count, offered.data()))) return false;
    return HasExtension(offered, name);
}

// Whether the device can follow the eyes, as the runtime says it in a short instance of its own (asked for XR_EXT_eye_gaze_interaction, which is the standard
// way): 1 yes, 0 no, -1 not known. Nothing here may stop a game: every step that fails answers "not known".
int ProbeEyeTracking(const XrInstanceCreateInfo* info, const XrApiLayerCreateInfo* layerInfo, XrApiLayerNextInfo* next) {
    PFN_xrGetInstanceProcAddr getProc = next->nextGetInstanceProcAddr;
    PFN_xrEnumerateInstanceExtensionProperties enumerate = nullptr;
    if (!getProc || XR_FAILED(getProc(XR_NULL_HANDLE, "xrEnumerateInstanceExtensionProperties", (PFN_xrVoidFunction*)&enumerate)) || !enumerate) return -1;
    uint32_t count = 0;
    if (XR_FAILED(enumerate(nullptr, 0, &count, nullptr)) || count == 0) return -1;
    std::vector<XrExtensionProperties> offered(count, {XR_TYPE_EXTENSION_PROPERTIES});
    if (XR_FAILED(enumerate(nullptr, count, &count, offered.data()))) return -1;
    // A runtime that does not offer the standard extension has nothing to say about eyes.
    if (!HasExtension(offered, kEyeGaze)) return 0;
    if (!HasExtension(offered, kAndroidCreate)) return -1;
    const GpAndroidCreateInfo* android = nullptr;
    for (const XrBaseInStructure* link = (const XrBaseInStructure*)info->next; link; link = link->next) {
        if (link->type == kAndroidCreateType) android = (const GpAndroidCreateInfo*)link;
    }
    if (!android) return -1;
    GpAndroidCreateInfo probeAndroid{kAndroidCreateType, nullptr, android->applicationVM, android->applicationActivity};
    const char* names[] = {kAndroidCreate, kEyeGaze};
    XrInstanceCreateInfo probeInfo{XR_TYPE_INSTANCE_CREATE_INFO};
    probeInfo.next = &probeAndroid;
    probeInfo.applicationInfo = info->applicationInfo;
    probeInfo.enabledExtensionCount = 2;
    probeInfo.enabledExtensionNames = names;
    XrApiLayerCreateInfo below = *layerInfo;
    below.nextInfo = next->next;
    XrInstance probe = XR_NULL_HANDLE;
    if (XR_FAILED(next->nextCreateApiLayerInstance(&probeInfo, &below, &probe)) || probe == XR_NULL_HANDLE) return -1;
    int answer = -1;
    PFN_xrGetSystem getSystem = nullptr;
    PFN_xrGetSystemProperties getProperties = nullptr;
    PFN_xrDestroyInstance destroy = nullptr;
    getProc(probe, "xrGetSystem", (PFN_xrVoidFunction*)&getSystem);
    getProc(probe, "xrGetSystemProperties", (PFN_xrVoidFunction*)&getProperties);
    getProc(probe, "xrDestroyInstance", (PFN_xrVoidFunction*)&destroy);
    if (getSystem && getProperties) {
        XrSystemGetInfo systemInfo{XR_TYPE_SYSTEM_GET_INFO};
        systemInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
        XrSystemId system = XR_NULL_SYSTEM_ID;
        if (XR_SUCCEEDED(getSystem(probe, &systemInfo, &system))) {
            XrSystemEyeGazeInteractionPropertiesEXT gaze{XR_TYPE_SYSTEM_EYE_GAZE_INTERACTION_PROPERTIES_EXT};
            XrSystemProperties properties{XR_TYPE_SYSTEM_PROPERTIES, &gaze};
            if (XR_SUCCEEDED(getProperties(probe, system, &properties))) answer = gaze.supportsEyeGazeInteraction ? 1 : 0;
        }
    }
    if (destroy) destroy(probe);
    return answer;
}

// Whether the foveation that follows the eyes is left out of the instance: always when the player said so, never when the player said it is wanted; by default
// on a Pico (the runtime that crashes with it), unless it says the device has eye tracking. A probe that died (the marker is still there) is not tried again.
bool DropEyeFoveation(const XrInstanceCreateInfo* info, const XrApiLayerCreateInfo* layerInfo, XrApiLayerNextInfo* next) {
    bool asked = false;
    for (uint32_t i = 0; i < info->enabledExtensionCount; i++) if (strcmp(info->enabledExtensionNames[i], kEyeFoveation) == 0) asked = true;
    if (!asked) return false;
    const char* said = getenv("GAMEPORT_XR_EYE_FOVEATION");
    std::string mode = said ? Lower(said) : "auto";
    if (mode == "on") { LOGI("eye foveation: kept, as the player chose"); return false; }
    if (mode == "off") { LOGI("eye foveation: left out, as the player chose"); return true; }
    const char* told = getenv("GAMEPORT_XR_FAMILY");
    if (!told || Lower(told).find("pico") == std::string::npos) return false;
    const std::string marker = GamePortFolder() + "xr_probe_pending.txt";
    if (FILE* leftover = fopen(marker.c_str(), "r")) {
        fclose(leftover);
        LOGI("eye foveation: left out, the check of the device did not end the last time");
        return true;
    }
    if (FILE* pending = fopen(marker.c_str(), "w")) { fputs("1\n", pending); fclose(pending); }
    int eyes = ProbeEyeTracking(info, layerInfo, next);
    remove(marker.c_str());
    LOGI("eye foveation: the device %s", eyes == 1 ? "has eye tracking, kept" : (eyes == 0 ? "has no eye tracking, left out" : "does not say, left out"));
    return eyes != 1;
}

XrResult XRAPI_CALL Layer_xrCreateApiLayerInstance(const XrInstanceCreateInfo* info, const XrApiLayerCreateInfo* layerInfo, XrInstance* instance) {
    XrApiLayerNextInfo* next = layerInfo->nextInfo;
    g_nextGetInstanceProcAddr = next->nextGetInstanceProcAddr;

    XrApiLayerCreateInfo below = *layerInfo;
    below.nextInfo = next->next;
    // Pico's runtime takes some of Meta's extensions and then crashes inside itself when the game starts to use them on a device that cannot do what they say
    // (Moss: the foveation that follows the eyes, on a device without eye tracking). Whether the extension is passed on is the player's choice for this game
    // (on, off), and by default it follows what the device says it can do.
    // And Pico's own controllers (their interaction profiles) exist for a game only when the instance has Pico's extension for them: a game made for another device
    // never asks for it, and the bindings translated onto Pico's controllers would be refused. It is added when the runtime offers it.
    std::vector<const char*> kept;
    XrInstanceCreateInfo filtered;
    const XrInstanceCreateInfo* toPass = info;
    if (info) {
        const bool dropEye = DropEyeFoveation(info, layerInfo, next);
        const std::set<std::string> hidden = HiddenExtensions();
        bool hideAny = false;
        for (uint32_t i = 0; i < info->enabledExtensionCount; i++) if (hidden.count(info->enabledExtensionNames[i])) hideAny = true;
        const char* family = getenv("GAMEPORT_XR_FAMILY");
        bool hasPicoControllers = false;
        for (uint32_t i = 0; i < info->enabledExtensionCount; i++) if (strcmp(info->enabledExtensionNames[i], kPicoControllers) == 0) hasPicoControllers = true;
        const bool addPicoControllers = family && Lower(family).find("pico") != std::string::npos && !hasPicoControllers && RuntimeOffers(next, kPicoControllers);
        const char* chosen = getenv("GAMEPORT_XR_ALPHA_BLEND");
        bool hasPassthrough = false;
        for (uint32_t i = 0; i < info->enabledExtensionCount; i++) if (strcmp(info->enabledExtensionNames[i], kFbPassthrough) == 0) hasPassthrough = true;
        // Added only to a game that enables XR_FB_swapchain_update_state, like the one the headset refused the alpha blend mode to: for any other game the instance is not touched.
        bool hasUpdateState = false;
        for (uint32_t i = 0; i < info->enabledExtensionCount; i++) if (strcmp(info->enabledExtensionNames[i], "XR_FB_swapchain_update_state") == 0) hasUpdateState = true;
        const bool addPassthrough = chosen && chosen[0] == '1' && hasUpdateState && !hasPassthrough && RuntimeOffers(next, kFbPassthrough);
        g_passthroughAdded.store(hasPassthrough || addPassthrough);
        // The controllers' extensions of Meta that a Pico's runtime lacks are offered by the layer to a game with Meta's OVRPlugin (see the patcher): they hold no function,
        // so they are not passed on, and the game goes on making the actions of its controllers, which the layer then translates.
        static const char* const kMetaControllerExtensions[] = {"XR_FB_touch_controller_pro", "XR_FB_touch_controller_proximity", "XR_META_touch_controller_plus"};
        const bool picoFamily = family && Lower(family).find("pico") != std::string::npos;
        auto isMetaController = [&](const char* name) {
            if (!picoFamily) return false;
            for (const char* known : kMetaControllerExtensions) if (strcmp(name, known) == 0) return !RuntimeOffers(next, name);
            return false;
        };
        bool dropMeta = false;
        for (uint32_t i = 0; i < info->enabledExtensionCount; i++) if (isMetaController(info->enabledExtensionNames[i])) dropMeta = true;
        // Test files that need an extension the game did not ask for: it is added when the runtime offers it.
        auto gameAsked = [&](const char* name) {
            for (uint32_t i = 0; i < info->enabledExtensionCount; i++) if (strcmp(info->enabledExtensionNames[i], name) == 0) return true;
            return false;
        };
        const bool addPerf = !ChosenLevels().empty() && !gameAsked("XR_EXT_performance_settings") && RuntimeOffers(next, "XR_EXT_performance_settings");
        const bool addRefresh = !ChosenRefreshRate().empty() && !gameAsked("XR_FB_display_refresh_rate") && RuntimeOffers(next, "XR_FB_display_refresh_rate");
        if (dropEye || hideAny || addPicoControllers || addPassthrough || dropMeta || addPerf || addRefresh) {
            for (uint32_t i = 0; i < info->enabledExtensionCount; i++) {
                if (isMetaController(info->enabledExtensionNames[i])) {
                    LOGI("extension %s not passed on: the runtime has none, the layer offered it", info->enabledExtensionNames[i]);
                    continue;
                }
                if (hidden.count(info->enabledExtensionNames[i])) {
                    LOGI("extension %s not passed on (test file)", info->enabledExtensionNames[i]);
                    continue;
                }
                if (dropEye && strcmp(info->enabledExtensionNames[i], kEyeFoveation) == 0) {
                    LOGI("extension %s not passed on", kEyeFoveation);
                    continue;
                }
                kept.push_back(info->enabledExtensionNames[i]);
            }
            if (addPassthrough) {
                kept.push_back(kFbPassthrough);
                LOGI("extension %s added: the player chose passthrough for this game", kFbPassthrough);
            }
            if (addPerf) {
                kept.push_back("XR_EXT_performance_settings");
                LOGI("extension XR_EXT_performance_settings added (test file)");
            }
            if (addRefresh) {
                kept.push_back("XR_FB_display_refresh_rate");
                LOGI("extension XR_FB_display_refresh_rate added (test file)");
            }
            if (addPicoControllers) {
                kept.push_back(kPicoControllers);
                LOGI("extension %s added: the game did not ask for it, and Pico's controllers need it", kPicoControllers);
            }
            filtered = *info;
            filtered.enabledExtensionCount = (uint32_t)kept.size();
            filtered.enabledExtensionNames = kept.data();
            toPass = &filtered;
        }
    }
    XrResult result = next->nextCreateApiLayerInstance(toPass, &below, instance);
    if (XR_FAILED(result)) return result;

    g_instance = *instance;
    Load("xrCreateReferenceSpace", g_createReferenceSpace);
    Load("xrDestroySpace", g_destroySpace);
    Load("xrLocateSpace", g_locateSpace);
    Load("xrLocateSpaces", g_locateSpaces);
    Load("xrLocateViews", g_locateViews);
    Load("xrEndFrame", g_endFrame);
    Load("xrEnumerateEnvironmentBlendModes", g_enumerateBlendModes);
    Load("xrWaitFrame", g_waitFrame);
    Load("xrBeginSession", g_beginSession);
    Load("xrCreateSwapchain", g_createSwapchain);
    Load("xrSuggestInteractionProfileBindings", g_suggestBindings);
    Load("xrApplyHapticFeedback", g_applyHaptic);
    Load("xrDestroySwapchain", g_destroySwapchain);
    Load("xrStringToPath", g_stringToPath);
    Load("xrCreateAction", g_createAction);
    Load("xrEndSession", g_endSession);
    Load("xrRequestExitSession", g_requestExit);
    Load("xrDestroySession", g_destroySession);
    Load("xrDestroyInstance", g_destroyInstance);
    Load("xrPathToString", g_pathToString);
    Load("xrGetCurrentInteractionProfile", g_getCurrentProfile);
    Load("xrEnumerateViewConfigurationViews", g_enumerateViews);
    Load("xrEnumerateSwapchainFormats", g_enumerateSwapchainFormats);
    Load("xrAttachSessionActionSets", g_attachActionSets);
    Load("xrPollEvent", g_pollEvent);
    Load("xrGetReferenceSpaceBoundsRect", g_getBounds);
    Load("xrGetActionStateBoolean", g_getActionBoolean);
    Load("xrGetActionStateFloat", g_getActionFloat);
    Load("xrCreateActionSet", g_createActionSet);
    Load("xrGetActionStateVector2f", g_getActionVector2);
    Load("xrSyncActions", g_syncActions);
    // A new run starts without the mark of the last one.
    remove((GamePortFolder() + "xr_silent.txt").c_str());
    Load("xrGetInstanceProperties", g_getInstanceProperties);
    Load("xrGetSystemProperties", g_getSystemProperties);
    Load("xrCreateVulkanDeviceKHR", g_createVulkanDevice);
    Load("xrPerfSettingsSetPerformanceLevelEXT", g_perfLevel);
    Load("xrRequestDisplayRefreshRateFB", g_refreshRequest);
    Load("xrEnumerateDisplayRefreshRatesFB", g_refreshEnumerate);
    // A new run starts without the report of the last one.
    remove((GamePortFolder() + "xr_refresh.txt").c_str());
    RemoveStaleProfilesText(GamePortFolder());

    // Which controllers this device has: GamePort says (its VrPlatform), else the runtime's name tells.
    auto familyOf = [](const std::string& text) {
        if (text.find("pico") != std::string::npos || text.find("bytedance") != std::string::npos) return Family::Pico;
        if (text.find("meta") != std::string::npos || text.find("oculus") != std::string::npos || text.find("quest") != std::string::npos) return Family::Meta;
        return Family::Unknown;
    };
    const char* said = getenv("GAMEPORT_XR_FAMILY");
    std::string runtime;
    XrInstanceProperties properties{XR_TYPE_INSTANCE_PROPERTIES};
    if (g_getInstanceProperties && XR_SUCCEEDED(g_getInstanceProperties(g_instance, &properties))) runtime = Lower(properties.runtimeName);
    g_family = familyOf(said ? Lower(said) : "");
    if (g_family == Family::Unknown) g_family = familyOf(runtime);
    LOGI("controller family %d (GamePort said '%s', runtime '%s')", (int)g_family, said ? said : "", runtime.c_str());
    // What this runtime offers: a game that asks for something absent, or that a runtime takes and then crashes on, is told by comparing with the list below.
    {
        PFN_xrEnumerateInstanceExtensionProperties enumerate = nullptr;
        if (XR_SUCCEEDED(g_nextGetInstanceProcAddr(XR_NULL_HANDLE, "xrEnumerateInstanceExtensionProperties", (PFN_xrVoidFunction*)&enumerate)) && enumerate) {
            uint32_t count = 0;
            if (XR_SUCCEEDED(enumerate(nullptr, 0, &count, nullptr)) && count > 0) {
                std::vector<XrExtensionProperties> offered(count, {XR_TYPE_EXTENSION_PROPERTIES});
                if (XR_SUCCEEDED(enumerate(nullptr, count, &count, offered.data()))) {
                    std::string names;
                    for (const auto& e : offered) names += std::string(names.empty() ? "" : " ") + e.extensionName;
                    LOGI("the runtime offers %u extension(s): %s", count, names.c_str());
                }
            }
        }
    }
    {
        const char* alpha = getenv("GAMEPORT_XR_ALPHA_BLEND");
        g_offerAlphaBlend.store(alpha && alpha[0] == '1');
        g_alphaByLayerBit.store(g_offerAlphaBlend.load() && g_family == Family::Pico);
        g_countRate.store(g_family == Family::Pico);
    }
    const char* prefer = getenv("GAMEPORT_XR_PREFER_FRAME");
    g_preferFrame = prefer && strcmp(prefer, "1") == 0;
    bool unknownPresent = false;
    const char* presentText = getenv("GAMEPORT_XR_PRESENT");
    g_present = static_cast<int>(ParsePresent(presentText, &unknownPresent));
    if (unknownPresent) LOGI("GAMEPORT_XR_PRESENT '%s' is not understood: the automatic choice is used", presentText);
    // "frame" is the existing preference of the Steam Frame's controls: the same variable's path, nothing new.
    if (g_present.load() == static_cast<int>(Present::Frame)) g_preferFrame = true;
    const char* floor = getenv("GAMEPORT_XR_LOCAL_FLOOR");
    g_recenterMode = floor && (strcmp(floor, "on") == 0 || strcmp(floor, "1") == 0) ? 1 : (floor && strcmp(floor, "off") == 0 ? 2 : 0);
    if (g_recenterMode.load() != 0) LOGI("recentering of the headset: %s, as the player chose", g_recenterMode.load() == 1 ? "followed" : "left to the game");
    if (g_preferFrame) LOGI("the player asked for the Steam Frame's controls where the game has them");
    if (g_present.load() == static_cast<int>(Present::Touch)) LOGI("the player asked for the Touch controls where the device has none of its own");
    g_hapticPercent = gp::ParseHapticPercent(getenv("GAMEPORT_XR_HAPTICS"));
    if (g_hapticPercent.load() != 100) LOGI("vibration strength: %d%%, as the player chose", g_hapticPercent.load());

    const char* seated = getenv("GAMEPORT_XR_SEATED");
    const char* eye = getenv("GAMEPORT_XR_EYE_CM");
    g_seated = seated && strcmp(seated, "1") == 0 && eye && atoi(eye) > 0;
    g_targetEyeMeters = g_seated ? atoi(eye) / 100.0f : 0.0f;
    LOGI("GamePort OpenXR layer active; seated mode %s (target eye height %.2f m); extensions the game asked for:", g_seated ? "on" : "off", g_targetEyeMeters);
    for (uint32_t i = 0; info && i < info->enabledExtensionCount; i++) LOGI("  %s", info->enabledExtensionNames[i]);
    return result;
}

}  // namespace

extern "C" __attribute__((visibility("default"))) XrResult XRAPI_CALL xrNegotiateLoaderApiLayerInterface(
    const XrNegotiateLoaderInfo* loaderInfo, const char*, XrNegotiateApiLayerRequest* request) {
    if (!loaderInfo || !request) return XR_ERROR_INITIALIZATION_FAILED;
    request->layerInterfaceVersion = XR_CURRENT_LOADER_API_LAYER_VERSION;
    request->layerApiVersion = XR_CURRENT_API_VERSION;
    request->getInstanceProcAddr = Layer_xrGetInstanceProcAddr;
    request->createApiLayerInstance = Layer_xrCreateApiLayerInstance;
    return XR_SUCCESS;
}
