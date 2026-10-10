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
#include <jni.h>
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
#include <openxr/openxr.h>
#include <openxr/openxr_loader_negotiation.h>
#include <openxr/openxr_platform.h>

#include "controller_allocation.h"

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
PFN_xrWaitFrame g_waitFrame = nullptr;
PFN_xrBeginSession g_beginSession = nullptr;
PFN_xrCreateSwapchain g_createSwapchain = nullptr;
PFN_xrSuggestInteractionProfileBindings g_suggestBindings = nullptr;
PFN_xrDestroySwapchain g_destroySwapchain = nullptr;
PFN_xrStringToPath g_stringToPath = nullptr;
PFN_xrPathToString g_pathToString = nullptr;
PFN_xrGetCurrentInteractionProfile g_getCurrentProfile = nullptr;

PFN_xrAttachSessionActionSets g_attachActionSets = nullptr;
PFN_xrPollEvent g_pollEvent = nullptr;
PFN_xrGetReferenceSpaceBoundsRect g_getBounds = nullptr;
PFN_xrGetActionStateBoolean g_getActionBoolean = nullptr;
PFN_xrGetActionStateFloat g_getActionFloat = nullptr;
PFN_xrGetInstanceProperties g_getInstanceProperties = nullptr;
PFN_xrEnumerateViewConfigurationViews g_enumerateViews = nullptr;

struct Size { uint32_t width, height; };
std::unordered_map<XrSwapchain, Size> g_swapchainSize;  // what each swapchain really is (guarded by g_mutex)
std::atomic<int> g_endFrameFailures{0};
// Once the runtime is seen to refuse the game's layers: 0 = submit as they are, 1 = without the
// placeholder quads, 2 = without any quad (the projection layers only).
std::atomic<int> g_dropMode{0};

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
        LOGI("reference space type %d created (floor based: %d)", (int)info->referenceSpaceType, (int)IsFloorBased(info->referenceSpaceType));
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

XrResult XRAPI_CALL Layer_xrEndFrame(XrSession session, const XrFrameEndInfo* endInfo) {
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
            if (!changed) continue;
            projectionViews.push_back(std::move(views));
            projections.push_back(*source);
            projections.back().views = projectionViews.back().data();
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
    XrFrameEndInfo shifted = *endInfo;
    shifted.layers = submitted.data();
    shifted.layerCount = (uint32_t)submitted.size();
    XrResult result = g_endFrame(session, &shifted);
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

XrResult XRAPI_CALL Layer_xrWaitFrame(XrSession session, const XrFrameWaitInfo* info, XrFrameState* state) {
    XrResult result = g_waitFrame(session, info, state);
    if (XR_SUCCEEDED(result) && state) Calibrate(state->predictedDisplayTime);
    return result;
}

XrResult XRAPI_CALL Layer_xrBeginSession(XrSession session, const XrSessionBeginInfo* info) {
    XrResult result = g_beginSession(session, info);
    if (XR_SUCCEEDED(result)) {
        g_session = session;
        g_samples = 0;
        g_sum = 0.0;
        g_calibrating = g_seated;
        g_offset.store(0.0f);
    }
    return result;
}

// Size of the last eye swapchain the runtime accepted. Some games pick a plugin written for another
// headset, which then asks for swapchains of 0x0; the runtime refuses those and the game quits.
uint32_t g_lastEyeWidth = 0;
uint32_t g_lastEyeHeight = 0;

XrResult XRAPI_CALL Layer_xrCreateSwapchain(XrSession session, const XrSwapchainCreateInfo* info, XrSwapchain* swapchain) {
    XrSwapchainCreateInfo fixed;
    if (info && (info->width == 0 || info->height == 0) && g_lastEyeWidth > 0 && g_lastEyeHeight > 0) {
        fixed = *info;
        fixed.width = g_lastEyeWidth;
        fixed.height = g_lastEyeHeight;
        LOGI("xrCreateSwapchain asked for %ux%u; using the eye size %ux%u instead", info->width, info->height, fixed.width, fixed.height);
        info = &fixed;
    }
    XrResult result = g_createSwapchain(session, info, swapchain);
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
            LOGI("view configuration %d view %u: recommended %ux%u, max %ux%u, samples %u/%u", (int)type, i, views[i].recommendedImageRectWidth,
                 views[i].recommendedImageRectHeight, views[i].maxImageRectWidth, views[i].maxImageRectHeight,
                 views[i].recommendedSwapchainSampleCount, views[i].maxSwapchainSampleCount);
            if (views[i].recommendedImageRectWidth == 0) views[i].recommendedImageRectWidth = views[i].maxImageRectWidth;
            if (views[i].recommendedImageRectHeight == 0) views[i].recommendedImageRectHeight = views[i].maxImageRectHeight;
        }
    }
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
        result = g_suggestBindings(instance, &remapped);
        LOGI("%s controls translated %s: %u binding(s) for the device (%u given, %d without an equivalent:%s) -> %d", sourceKind,
             attempt == 0 ? "with the overrides" : attempt == 1 ? "without moves between hands" : "with the defaults only",
             (unsigned)bindings.size(), (unsigned)given.size(), droppedCount, dropped.c_str(), (int)result);
    }
    LOGI("%s controls, where each went:%s", sourceKind, tableText.c_str());
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
    }
    return XR_SUCCEEDED(result);
}

// A runtime may refuse a whole table for the sake of one binding (an action it no longer knows, a path the profile does not have), and the game is then left
// with no control at all. The bindings are tried one at a time, to find which it refuses, and the others are given together. Only runs after a refusal.
XrResult SuggestTolerant(XrInstance instance, const XrInteractionProfileSuggestedBinding* suggested, const char* profile) {
    XrResult whole = g_suggestBindings(instance, suggested);
    if (XR_SUCCEEDED(whole) || whole == XR_ERROR_PATH_UNSUPPORTED || !suggested || suggested->countSuggestedBindings < 2) return whole;
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
    std::string names;
    for (const std::string& name : refused) names += " " + name;
    LOGI("the runtime refused the %u bindings of %s (%d); %zu of them are refused one by one:%s, %zu mended; the %zu left are given: %d", suggested->countSuggestedBindings,
         profile, (int)whole, refused.size(), names.c_str(), mended, accepted.size(), (int)result);
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

// Runs once the game has said everything it knows, right before it attaches its action sets.
void EmulateIfNeeded(XrInstance instance) {
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
        const std::string touch = richest(kMetaProfiles, sizeof(kMetaProfiles) / sizeof(*kMetaProfiles));
        if (!touch.empty() && EmulateFrom(instance, touch, "touch", stash[touch])) return;
    }
    if (!valve.empty()) EmulateFrom(instance, valve, "valve", stash[valve]);
}

XrResult XRAPI_CALL Layer_xrSuggestInteractionProfileBindings(XrInstance instance, const XrInteractionProfileSuggestedBinding* suggested) {
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

XrResult XRAPI_CALL Layer_xrAttachSessionActionSets(XrSession session, const XrSessionActionSetsAttachInfo* info) {
    EmulateIfNeeded(g_instance);
    return g_attachActionSets(session, info);
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
        {"xrWaitFrame", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrWaitFrame)},
        {"xrBeginSession", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrBeginSession)},
        {"xrCreateSwapchain", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrCreateSwapchain)},
        {"xrDestroySwapchain", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrDestroySwapchain)},
        {"xrEnumerateViewConfigurationViews", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrEnumerateViewConfigurationViews)},
        {"xrGetCurrentInteractionProfile", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetCurrentInteractionProfile)},
        {"xrAttachSessionActionSets", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrAttachSessionActionSets)},
        {"xrSuggestInteractionProfileBindings", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrSuggestInteractionProfileBindings)},
        {"xrPollEvent", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrPollEvent)},
        {"xrStringToPath", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrStringToPath)},
        {"xrGetActionStateBoolean", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetActionStateBoolean)},
        {"xrGetActionStateFloat", reinterpret_cast<PFN_xrVoidFunction>(Layer_xrGetActionStateFloat)},
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

XrResult XRAPI_CALL Layer_xrCreateApiLayerInstance(const XrInstanceCreateInfo* info, const XrApiLayerCreateInfo* layerInfo, XrInstance* instance) {
    XrApiLayerNextInfo* next = layerInfo->nextInfo;
    g_nextGetInstanceProcAddr = next->nextGetInstanceProcAddr;

    XrApiLayerCreateInfo below = *layerInfo;
    below.nextInfo = next->next;
    XrResult result = next->nextCreateApiLayerInstance(info, &below, instance);
    if (XR_FAILED(result)) return result;

    g_instance = *instance;
    Load("xrCreateReferenceSpace", g_createReferenceSpace);
    Load("xrDestroySpace", g_destroySpace);
    Load("xrLocateSpace", g_locateSpace);
    Load("xrLocateSpaces", g_locateSpaces);
    Load("xrLocateViews", g_locateViews);
    Load("xrEndFrame", g_endFrame);
    Load("xrWaitFrame", g_waitFrame);
    Load("xrBeginSession", g_beginSession);
    Load("xrCreateSwapchain", g_createSwapchain);
    Load("xrSuggestInteractionProfileBindings", g_suggestBindings);
    Load("xrDestroySwapchain", g_destroySwapchain);
    Load("xrStringToPath", g_stringToPath);
    Load("xrPathToString", g_pathToString);
    Load("xrGetCurrentInteractionProfile", g_getCurrentProfile);
    Load("xrEnumerateViewConfigurationViews", g_enumerateViews);
    Load("xrAttachSessionActionSets", g_attachActionSets);
    Load("xrPollEvent", g_pollEvent);
    Load("xrGetReferenceSpaceBoundsRect", g_getBounds);
    Load("xrGetActionStateBoolean", g_getActionBoolean);
    Load("xrGetActionStateFloat", g_getActionFloat);
    // A new run starts without the mark of the last one.
    remove((GamePortFolder() + "xr_silent.txt").c_str());
    Load("xrGetInstanceProperties", g_getInstanceProperties);

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
    const char* prefer = getenv("GAMEPORT_XR_PREFER_FRAME");
    g_preferFrame = prefer && strcmp(prefer, "1") == 0;
    const char* floor = getenv("GAMEPORT_XR_LOCAL_FLOOR");
    g_recenterMode = floor && (strcmp(floor, "on") == 0 || strcmp(floor, "1") == 0) ? 1 : (floor && strcmp(floor, "off") == 0 ? 2 : 0);
    if (g_recenterMode.load() != 0) LOGI("recentering of the headset: %s, as the player chose", g_recenterMode.load() == 1 ? "followed" : "left to the game");
    if (g_preferFrame) LOGI("the player asked for the Steam Frame's controls where the game has them");

    const char* seated = getenv("GAMEPORT_XR_SEATED");
    const char* eye = getenv("GAMEPORT_XR_EYE_CM");
    g_seated = seated && strcmp(seated, "1") == 0 && eye && atoi(eye) > 0;
    g_targetEyeMeters = g_seated ? atoi(eye) / 100.0f : 0.0f;
    LOGI("GamePort OpenXR layer active; seated mode %s (target eye height %.2f m); extensions:", g_seated ? "on" : "off", g_targetEyeMeters);
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
