// Shared by the host tests of what the layer does with the game's tables when it attaches its actions (EmulateIfNeeded / EmulateFrom): a fake runtime that records
// every call, a capture of the layer's log lines, synthetic tables of the shape of real games, and a runner that gives each case its own process (the layer keeps a few
// "once per process" flags). Include this file FIRST in a test: it includes the layer's own source, with its log redirected here.
#pragma once
#include <android/log.h>

#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <set>
#include <string>
#include <vector>

// Every line the layer logs and every call it makes to the runtime, in order.
inline std::vector<std::string> g_transcript;

inline int FixtureLog(int, const char*, const char* format, ...) {
    char text[4096];
    va_list args;
    va_start(args, format);
    vsnprintf(text, sizeof(text), format, args);
    va_end(args);
    g_transcript.push_back(std::string("LOG ") + text);
    return 0;
}
#define __android_log_print FixtureLog

#include "../gameport_xr_layer.cpp"

namespace fixture {

int g_failures = 0;
#define CHECK(condition)                                                                 \
    do {                                                                                 \
        if (!(condition)) {                                                              \
            std::printf("FAILED: %s (line %d)\n", #condition, __LINE__);                 \
            fixture::g_failures++;                                                       \
        }                                                                                \
    } while (0)

struct Runtime {
    std::map<std::string, XrPath> ids;
    std::map<XrPath, std::string> names;
    std::set<uint64_t> refusedActions;     // a table that holds one of these actions is refused...
    std::set<std::string> refusedProfiles; // ...and so is any table for one of these profiles
    XrResult refusal = XR_ERROR_PATH_UNSUPPORTED;
};
Runtime g_rt;

XrPath Id(const std::string& name) {
    auto it = g_rt.ids.find(name);
    if (it != g_rt.ids.end()) return it->second;
    const XrPath id = (XrPath)(g_rt.ids.size() + 1);
    g_rt.ids[name] = id;
    g_rt.names[id] = name;
    return id;
}

XrResult XRAPI_CALL FakeStringToPath(XrInstance, const char* text, XrPath* path) {
    *path = Id(text);
    return XR_SUCCESS;
}

XrResult XRAPI_CALL FakePathToString(XrInstance, XrPath path, uint32_t capacity, uint32_t* count, char* buffer) {
    const std::string& name = g_rt.names[path];
    snprintf(buffer, capacity, "%s", name.c_str());
    *count = (uint32_t)name.size() + 1;
    return XR_SUCCESS;
}

XrResult XRAPI_CALL FakeSuggest(XrInstance, const XrInteractionProfileSuggestedBinding* suggested) {
    uint64_t hash = 1469598103934665603ULL;  // FNV-1a over every (action, path) of the table, in order
    bool refused = g_rt.refusedProfiles.count(g_rt.names[suggested->interactionProfile]) > 0;
    for (uint32_t i = 0; i < suggested->countSuggestedBindings; i++) {
        const auto& b = suggested->suggestedBindings[i];
        const std::string item = std::to_string((uint64_t)b.action) + ":" + g_rt.names[b.binding] + ";";
        for (unsigned char c : item) { hash ^= c; hash *= 1099511628211ULL; }
        if (g_rt.refusedActions.count((uint64_t)b.action)) refused = true;
    }
    const XrResult result = refused ? g_rt.refusal : XR_SUCCESS;
    char line[256];
    snprintf(line, sizeof(line), "RT suggest %s n=%u h=%016llx -> %d", g_rt.names[suggested->interactionProfile].c_str(), suggested->countSuggestedBindings,
             (unsigned long long)hash, (int)result);
    g_transcript.push_back(line);
    return result;
}

// Paths of a Touch controller, the Steam Frame's controller, a controller of the Index kind.
const std::vector<std::string>& TouchVocab() {
    static const std::vector<std::string> v = [] {
        std::vector<std::string> out;
        for (const char* hand : {"left", "right"}) {
            const std::string h = std::string("/user/hand/") + hand;
            for (const char* p : {"trigger/value", "trigger/touch", "squeeze/value", "thumbstick", "thumbstick/click", "thumbstick/touch"}) out.push_back(h + "/input/" + p);
            if (std::string(hand) == "left") for (const char* p : {"x/click", "x/touch", "y/click", "y/touch", "menu/click"}) out.push_back(h + "/input/" + p);
            else for (const char* p : {"a/click", "a/touch", "b/click", "b/touch"}) out.push_back(h + "/input/" + p);
            for (const char* p : {"grip/pose", "aim/pose"}) out.push_back(h + "/input/" + p);
            out.push_back(h + "/output/haptic");
        }
        return out;
    }();
    return v;
}

const std::vector<std::string>& FrameVocab() {
    static const std::vector<std::string> v = [] {
        std::vector<std::string> out;
        for (const char* hand : {"left", "right"}) {
            const std::string h = std::string("/user/hand/") + hand;
            for (const char* p : {"trigger/value", "trigger/click", "squeeze/value", "squeeze/click", "thumbstick", "thumbstick/click", "thumbstick/touch", "dpad_up/click",
                                  "dpad_down/click", "dpad_left/click", "dpad_right/click", "bumper/click", "grip/pose", "aim/pose"})
                out.push_back(h + "/input/" + p);
            out.push_back(h + "/output/haptic");
        }
        for (const char* p : {"/user/hand/left/input/view/click", "/user/hand/right/input/menu/click", "/user/hand/right/input/a/click", "/user/hand/right/input/b/click",
                              "/user/hand/left/input/x/click", "/user/hand/left/input/y/click"})
            out.push_back(p);
        return out;
    }();
    return v;
}

const std::vector<std::string>& IndexVocab() {
    static const std::vector<std::string> v = {
        "/user/hand/left/input/trigger/value", "/user/hand/right/input/trigger/value", "/user/hand/left/input/thumbstick", "/user/hand/right/input/thumbstick",
        "/user/hand/left/input/a/click",       "/user/hand/right/input/a/click",       "/user/hand/left/input/grip/pose",  "/user/hand/right/input/grip/pose"};
    return v;
}

const std::vector<std::string>& SimpleVocab() {
    static const std::vector<std::string> v = {"/user/hand/left/input/select/click", "/user/hand/right/input/select/click", "/user/hand/left/input/menu/click",
                                               "/user/hand/right/input/menu/click", "/user/hand/left/input/grip/pose",      "/user/hand/right/input/grip/pose"};
    return v;
}

const std::vector<std::string>& GazeVocab() {
    static const std::vector<std::string> v = {"/user/eyes_ext/input/gaze_ext/pose"};
    return v;
}

const char* kTouch = "/interaction_profiles/oculus/touch_controller";
const char* kTouchPlus = "/interaction_profiles/meta/touch_controller_plus";
const char* kPico = "/interaction_profiles/bytedance/pico4_controller";
const char* kFrame = "/interaction_profiles/valve/frame_controller_valve";
const char* kIndex = "/interaction_profiles/valve/index_controller";
const char* kSimple = "/interaction_profiles/khr/simple_controller";
const char* kGaze = "/interaction_profiles/ext/eye_gaze_interaction";

// What the game suggested for [profile]: [count] bindings cycling over [vocab], actions numbered from [firstAction]. The actions of one table are its own (a game made
// for the Steam Frame makes its own actions for that profile).
void Game(const char* profile, const std::vector<std::string>& vocab, int count, int firstAction) {
    std::vector<XrActionSuggestedBinding> table;
    for (int i = 0; i < count; i++) {
        XrActionSuggestedBinding b{};
        b.action = reinterpret_cast<XrAction>(static_cast<uintptr_t>(firstAction + i));
        b.binding = Id(vocab[i % vocab.size()]);
        table.push_back(b);
        g_actionNames[(uint64_t)b.action] = "act" + std::to_string(firstAction + i);
    }
    g_stash[profile] = table;
    g_profileHandles[profile] = Id(profile);
}

// The actions made from [first] on, [count] of them, are refused by the runtime.
void Refuse(int first, int count) {
    for (int i = 0; i < count; i++) g_rt.refusedActions.insert((uint64_t)(first + i));
}

void Reset(Family family) {
    g_transcript.clear();
    g_rt = Runtime();
    g_stash.clear();
    g_profileHandles.clear();
    g_actionNames.clear();
    g_family = family;
    g_preferFrame = false;
    g_emulating = false;
    g_gameHasBoth = false;
    g_emulatedSourcePath = XR_NULL_PATH;
    g_stringToPath = FakeStringToPath;
    g_pathToString = FakePathToString;
    g_suggestBindings = FakeSuggest;
}

// The state the layer leaves, for the transcript.
void NoteState() {
    char line[512];
    snprintf(line, sizeof(line), "STATE emulating=%d gameHasBoth=%d told=%s", (int)g_emulating.load(), (int)g_gameHasBoth.load(),
             g_emulatedSourcePath == XR_NULL_PATH ? "-" : g_rt.names[g_emulatedSourcePath].c_str());
    g_transcript.push_back(line);
}

std::string Transcript() {
    std::string out;
    for (const std::string& line : g_transcript) out += line + "\n";
    return out;
}

// Lines of the transcript that the layer logs with [prefix] (without the "LOG " lead).
int CountLogs(const char* prefix) {
    int n = 0;
    for (const std::string& line : g_transcript) if (line.compare(0, 4, "LOG ") == 0 && line.compare(4, strlen(prefix), prefix) == 0) n++;
    return n;
}

bool Has(const std::string& part) {
    for (const std::string& line : g_transcript) if (line.find(part) != std::string::npos) return true;
    return false;
}

// Calls to the runtime for the profile [name] (the log lines are left out).
int Suggestions(const std::string& profile) {
    int n = 0;
    for (const std::string& line : g_transcript) if (line.compare(0, 11, "RT suggest ") == 0 && line.compare(11, profile.size(), profile) == 0) n++;
    return n;
}

struct Case { const char* name; void (*run)(); };
bool g_print = false;

// Each case runs in its own process: the layer keeps "once per process" flags. With a case's name as argument the case runs; without, every case is run.
int RunCases(int argc, char** argv, const std::vector<Case>& cases) {
    if (argc >= 3 && std::strcmp(argv[1], "--print") == 0) {
        g_print = true;
        for (const Case& c : cases) if (std::strcmp(c.name, argv[2]) == 0) { c.run(); return 0; }
        return 2;
    }
    if (argc >= 2) {
        for (const Case& c : cases) {
            if (std::strcmp(c.name, argv[1]) != 0) continue;
            c.run();
            return g_failures == 0 ? 0 : 1;
        }
        std::printf("no case named %s\n", argv[1]);
        return 2;
    }
    int failed = 0;
    for (const Case& c : cases) {
        const std::string command = std::string("\"") + argv[0] + "\" " + c.name;
        if (std::system(command.c_str()) != 0) {
            std::printf("FAILED case: %s\n", c.name);
            failed++;
        }
    }
    if (failed == 0) std::printf("all %zu cases passed\n", cases.size());
    return failed == 0 ? 0 : 1;
}

}  // namespace fixture
