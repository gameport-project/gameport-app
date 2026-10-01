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
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#define XR_USE_PLATFORM_ANDROID
#include <openxr/openxr.h>
#include <openxr/openxr_loader_negotiation.h>
#include <openxr/openxr_platform.h>

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

XrResult XRAPI_CALL Layer_xrCreateReferenceSpace(XrSession session, const XrReferenceSpaceCreateInfo* info, XrSpace* space) {
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
//   1. the game has bindings for a profile of this device: nothing to do;
//   2. else the game has bindings for the Meta (Touch) controllers: they are translated onto the device;
//   3. else the game has bindings for the Steam Frame's controllers: they are translated onto the device.
// A translation moves each control (a hand and a group: "left:thumbstick", "right:a") onto the device's
// own; the player can override any control per game (GAMEPORT_XR_MAP, filled from GamePort's controller
// page): "left:dpad_up=right:a;right:menu=left:menu;left:view=none". The game is told that the profile it
// knows is the active one, so it keeps reading the controllers.
// The device family comes from GamePort (GAMEPORT_XR_FAMILY, from its VrPlatform: "meta" or "pico"), else
// from the runtime's name.

enum class Family { Unknown, Meta, Pico };

constexpr const char* kValveProfile = "/interaction_profiles/valve/frame_controller_valve";
const char* const kMetaProfiles[] = {"/interaction_profiles/meta/touch_controller_plus", "/interaction_profiles/facebook/touch_controller_pro",
                                     "/interaction_profiles/oculus/touch_controller"};
const char* const kPicoProfiles[] = {"/interaction_profiles/bytedance/pico4_controller", "/interaction_profiles/bytedance/pico_neo3_controller"};

Family g_family = Family::Unknown;
std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>> g_stash;  // every suggestion of the game, by profile (g_mutex)
std::atomic<bool> g_emulating{false};
XrPath g_emulatedSourcePath = XR_NULL_PATH;  // the profile the game knows and is told is active

std::string PathText(XrInstance instance, XrPath path) {
    char text[XR_MAX_PATH_LENGTH] = {0};
    uint32_t written = 0;
    if (g_pathToString && path != XR_NULL_PATH) g_pathToString(instance, path, sizeof(text), &written, text);
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
bool DeviceHasGroup(const std::string& hand, const std::string& group) {
    static const char* both[] = {"trigger", "squeeze", "thumbstick", "thumbrest", "grip", "aim"};
    for (const char* g : both) if (group == g) return true;
    if (hand == "left") return group == "x" || group == "y" || group == "menu";
    return group == "a" || group == "b";
}

// The sub-input the device offers for a source sub-input: true with [out] set ("" meaning the group
// itself, as the joystick's two-axis value has no sub-input), or false when it has nothing for it.
bool DeviceSub(const std::string& group, const std::string& sub, std::string& out) {
    auto pick = [&](const char* value) { out = value; return true; };
    const bool pico = g_family == Family::Pico;
    if (group == "trigger") {
        if (sub == "click") return pick(pico ? "click" : "value");
        if (sub == "value") return pick("value");
        if (sub == "touch") return pick("touch");
        return false;
    }
    if (group == "squeeze") {
        if (sub == "click") return pick(pico ? "click" : "value");
        return sub == "value" ? pick("value") : false;
    }
    if (group == "thumbstick") {
        if (sub.empty()) return pick("");
        return (sub == "x" || sub == "y" || sub == "click" || sub == "touch") ? pick(sub.c_str()) : false;
    }
    if (group == "thumbrest") return sub == "touch" ? pick("touch") : false;
    if (group == "grip" || group == "aim") return sub == "pose" ? pick("pose") : false;
    // Buttons: a, b, x, y, menu. A float "value" also serves a boolean action (the runtime thresholds it).
    if (sub == "click" || sub == "value") return pick("click");
    if (sub == "touch" && group != "menu") return pick("touch");
    return false;
}

// "left:dpad_up=right:a;right:menu=left:menu;left:view=none" -> overrides by source control.
std::unordered_map<std::string, std::string> ParseOverrides() {
    std::unordered_map<std::string, std::string> overrides;
    const char* raw = getenv("GAMEPORT_XR_MAP");
    if (!raw) return overrides;
    std::string all = raw;
    size_t start = 0;
    while (start < all.size()) {
        size_t end = all.find(';', start);
        if (end == std::string::npos) end = all.size();
        const std::string entry = all.substr(start, end - start);
        const size_t eq = entry.find('=');
        if (eq != std::string::npos) overrides[entry.substr(0, eq)] = entry.substr(eq + 1);
        start = end + 1;
    }
    return overrides;
}

// Where a source control goes: "hand:group", or "" for nowhere.
std::string TargetFor(const Control& source, const std::unordered_map<std::string, std::string>& overrides, bool useOverrides) {
    const std::string key = source.hand + ":" + source.group;
    if (useOverrides) {
        auto it = overrides.find(key);
        if (it != overrides.end()) return it->second == "none" ? "" : it->second;
    }
    if (DeviceHasGroup(source.hand, source.group)) return key;
    // The Steam Frame's left D-pad has no equivalent here: up goes to Y and down to X, the buttons above and below it.
    if (source.hand == "left" && source.group == "dpad_up") return "left:y";
    if (source.hand == "left" && source.group == "dpad_down") return "left:x";
    // The Steam Frame has its menu button on the right controller; these controllers only have one, on the left.
    if (source.hand == "right" && source.group == "menu") return "left:menu";
    return "";
}

// Tells GamePort which family the game was translated from and which controls it uses, so the controller
// page is offered for this game.
void WriteDetectedControls(const char* source, const std::vector<std::string>& groups) {
    char cmd[256] = {0};
    FILE* in = fopen("/proc/self/cmdline", "r");
    if (!in) return;
    size_t n = fread(cmd, 1, sizeof(cmd) - 1, in);
    fclose(in);
    cmd[n] = 0;
    const std::string path = std::string("/storage/emulated/0/Android/data/") + cmd + "/files/gameport/xr_controls.txt";
    FILE* out = fopen(path.c_str(), "w");
    if (!out) return;
    fprintf(out, "source=%s\n", source);
    for (const auto& g : groups) fprintf(out, "%s\n", g.c_str());
    fclose(out);
}

// Translates the game's bindings for [source] onto the device's primary profile.
bool EmulateFrom(XrInstance instance, const std::string& sourceProfile, const char* sourceKind, const std::vector<XrActionSuggestedBinding>& given) {
    const auto overrides = ParseOverrides();
    std::vector<std::string> detected;
    std::string dropped;
    int droppedCount = 0;

    struct Built { XrActionSuggestedBinding binding; bool crossHand; };
    auto build = [&](bool useOverrides) {
        std::vector<Built> built;
        detected.clear();
        dropped.clear();
        droppedCount = 0;
        for (const XrActionSuggestedBinding& binding : given) {
            const std::string source = PathText(instance, binding.binding);
            std::string mapped;
            bool cross = false;
            Control control;
            if (ParseInput(source, control)) {
                if (control.group != "grip" && control.group != "aim") {
                    const std::string key = control.hand + ":" + control.group;
                    if (std::find(detected.begin(), detected.end(), key) == detected.end()) detected.push_back(key);
                }
                const std::string target = TargetFor(control, overrides, useOverrides);
                const size_t colon = target.find(':');
                if (colon != std::string::npos) {
                    const std::string thand = target.substr(0, colon), tgroup = target.substr(colon + 1);
                    std::string tsub;
                    if (DeviceHasGroup(thand, tgroup) && DeviceSub(tgroup, control.sub, tsub)) {
                        mapped = "/user/hand/" + thand + "/input/" + tgroup + (tsub.empty() ? "" : "/" + tsub);
                        cross = thand != control.hand;
                    }
                }
            } else if (source == "/user/hand/left/output/haptic" || source == "/user/hand/right/output/haptic") {
                mapped = source;
            }
            XrPath mappedPath = XR_NULL_PATH;
            if (mapped.empty() || XR_FAILED(g_stringToPath(instance, mapped.c_str(), &mappedPath))) {
                if (droppedCount++ < 24) dropped += " " + source;
                continue;
            }
            bool duplicate = false;
            for (const auto& existing : built) if (existing.binding.action == binding.action && existing.binding.binding == mappedPath) duplicate = true;
            if (!duplicate) built.push_back({{binding.action, mappedPath}, cross});
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
    static bool reported = false;
    if (!reported) {
        reported = true;
        WriteDetectedControls(sourceKind, detected);
    }
    if (XR_SUCCEEDED(result)) {
        g_stringToPath(instance, sourceProfile.c_str(), &g_emulatedSourcePath);
        g_emulating.store(true);
    }
    return XR_SUCCEEDED(result);
}

// Runs once the game has said everything it knows, right before it attaches its action sets.
void EmulateIfNeeded(XrInstance instance) {
    if (g_family == Family::Unknown || !g_stringToPath || !g_pathToString) return;
    std::unordered_map<std::string, std::vector<XrActionSuggestedBinding>> stash;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        stash = g_stash;
    }
    for (const auto& [profile, bindings] : stash) {
        if (IsNativeProfile(profile)) {
            LOGI("the game has its own bindings for this device's controllers (%s): nothing to translate", profile.c_str());
            g_emulating.store(false);
            return;
        }
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
    auto valve = stash.find(kValveProfile);
    if (valve != stash.end()) EmulateFrom(instance, kValveProfile, "valve", valve->second);
}

XrResult XRAPI_CALL Layer_xrSuggestInteractionProfileBindings(XrInstance instance, const XrInteractionProfileSuggestedBinding* suggested) {
    std::string profile;
    if (suggested && g_pathToString) {
        profile = PathText(instance, suggested->interactionProfile);
        std::lock_guard<std::mutex> lock(g_mutex);
        g_stash[profile].assign(suggested->suggestedBindings, suggested->suggestedBindings + suggested->countSuggestedBindings);
    }
    XrResult result = g_suggestBindings(instance, suggested);
    if (suggested) {
        LOGI("xrSuggestInteractionProfileBindings(%s, %u bindings) -> %d", profile.c_str(), suggested->countSuggestedBindings, (int)result);
    }
    // A profile the device does not have is not an error worth failing the game over: it may be translated later.
    if (result == XR_ERROR_PATH_UNSUPPORTED && g_family != Family::Unknown && !IsNativeProfile(profile)) return XR_SUCCESS;
    return result;
}

XrResult XRAPI_CALL Layer_xrAttachSessionActionSets(XrSession session, const XrSessionActionSetsAttachInfo* info) {
    EmulateIfNeeded(g_instance);
    return g_attachActionSets(session, info);
}

// While a translation is on, the game is told that the profile it knows is the one in use.
XrResult XRAPI_CALL Layer_xrGetCurrentInteractionProfile(XrSession session, XrPath userPath, XrInteractionProfileState* state) {
    XrResult result = g_getCurrentProfile(session, userPath, state);
    if (XR_SUCCEEDED(result) && state && g_emulating.load() && state->interactionProfile != XR_NULL_PATH) {
        if (IsNativeProfile(PathText(g_instance, state->interactionProfile))) state->interactionProfile = g_emulatedSourcePath;
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
