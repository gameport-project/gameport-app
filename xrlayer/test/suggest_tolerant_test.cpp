// Host test of the way the layer gives a game's bindings to the runtime (SuggestTolerant): what it does when the runtime refuses a whole table, and what it leaves
// alone. The runtime is a fake; the layer's own source is included so its functions can be called. Run by scripts/test_xr_layer.sh when the OpenXR headers are there.
#include <cstdio>
#include <map>

#include "../gameport_xr_layer.cpp"

namespace {

int g_failures = 0;
#define CHECK(condition)                                                                 \
    do {                                                                                 \
        if (!(condition)) {                                                              \
            std::printf("FAILED: %s (line %d)\n", #condition, __LINE__);                 \
            g_failures++;                                                                \
        }                                                                                \
    } while (0)

// The fake runtime: some paths are "bad" (it refuses a table that holds one, with the error it is told to give); every call is recorded.
struct FakeRuntime {
    std::set<XrPath> bad;
    XrResult error = XR_ERROR_PATH_UNSUPPORTED;
    // A table refused as a whole, even when each binding is fine alone (a runtime that dislikes the combination).
    bool refuseWhole = false;
    std::vector<std::vector<XrPath>> calls;
    std::map<XrPath, std::string> names;
};
FakeRuntime g_fake;

XrResult XRAPI_CALL FakeSuggest(XrInstance, const XrInteractionProfileSuggestedBinding* suggested) {
    std::vector<XrPath> paths;
    for (uint32_t i = 0; i < suggested->countSuggestedBindings; i++) paths.push_back(suggested->suggestedBindings[i].binding);
    g_fake.calls.push_back(paths);
    for (XrPath path : paths) if (g_fake.bad.count(path)) return g_fake.error;
    if (g_fake.refuseWhole && paths.size() > 1) return g_fake.error;
    return XR_SUCCESS;
}

XrResult XRAPI_CALL FakePathToString(XrInstance, XrPath path, uint32_t capacity, uint32_t* count, char* buffer) {
    const std::string& name = g_fake.names[path];
    snprintf(buffer, capacity, "%s", name.c_str());
    *count = (uint32_t)name.size() + 1;
    return XR_SUCCESS;
}

struct Table {
    std::vector<XrActionSuggestedBinding> bindings;
    XrInteractionProfileSuggestedBinding suggestion{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
};

// A table of [count] bindings, paths 100.., the one at [badAt] (when 0 or more) marked as bad.
Table MakeTable(int count, int badAt, XrResult error = XR_ERROR_PATH_UNSUPPORTED) {
    g_fake = FakeRuntime();
    g_fake.error = error;
    Table table;
    for (int i = 0; i < count; i++) {
        XrActionSuggestedBinding binding{};
        binding.action = reinterpret_cast<XrAction>(static_cast<uintptr_t>(1000 + i));
        binding.binding = (XrPath)(100 + i);
        table.bindings.push_back(binding);
        g_fake.names[binding.binding] = "/user/hand/left/input/control" + std::to_string(i) + "/click";
        if (i == badAt) g_fake.bad.insert(binding.binding);
    }
    table.suggestion.interactionProfile = (XrPath)7;
    table.suggestion.countSuggestedBindings = (uint32_t)table.bindings.size();
    table.suggestion.suggestedBindings = table.bindings.data();
    return table;
}

const char* kOwn = "/interaction_profiles/oculus/touch_controller";
const char* kOther = "/interaction_profiles/valve/index_controller";

void Setup() {
    g_family = Family::Meta;
    g_suggestBindings = FakeSuggest;
    g_pathToString = FakePathToString;
    g_stringToPath = nullptr;
}

void AcceptedTableIsGivenOnce() {
    Table t = MakeTable(10, -1);
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOwn) == XR_SUCCESS);
    CHECK(g_fake.calls.size() == 1);
}

void OwnProfileRefusedWithPathUnsupportedGivesTheOthers() {
    Table t = MakeTable(10, 4);
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOwn) == XR_SUCCESS);
    const auto& last = g_fake.calls.back();
    CHECK(last.size() == 9);
    CHECK(std::find(last.begin(), last.end(), (XrPath)104) == last.end());
}

void OtherProfileRefusedWithPathUnsupportedIsLeftAlone() {
    Table t = MakeTable(10, 4);
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOther) == XR_ERROR_PATH_UNSUPPORTED);
    CHECK(g_fake.calls.size() == 1);
}

void OtherErrorsStillGoOneByOneAsBefore() {
    Table t = MakeTable(10, 4, XR_ERROR_HANDLE_INVALID);
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOwn) == XR_SUCCESS);
    CHECK(g_fake.calls.back().size() == 9);
    Table other = MakeTable(10, 4, XR_ERROR_HANDLE_INVALID);
    CHECK(SuggestTolerant(nullptr, &other.suggestion, kOther) == XR_SUCCESS);
}

void NothingAcceptedKeepsTheRefusal() {
    Table t = MakeTable(3, -1);
    for (const auto& binding : t.bindings) g_fake.bad.insert(binding.binding);
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOwn) == XR_ERROR_PATH_UNSUPPORTED);
}

void ASingleBindingIsNotTriedAgain() {
    Table t = MakeTable(1, 0);
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOwn) == XR_ERROR_PATH_UNSUPPORTED);
    CHECK(g_fake.calls.size() == 1);
}

void AWholeRefusalWithNoBadBindingKeepsTheRefusal() {
    Table t = MakeTable(5, -1);
    g_fake.refuseWhole = true;
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOwn) == XR_ERROR_PATH_UNSUPPORTED);
}

void WithoutAKnownFamilyNothingChanges() {
    g_family = Family::Unknown;
    Table t = MakeTable(10, 4);
    CHECK(SuggestTolerant(nullptr, &t.suggestion, kOwn) == XR_ERROR_PATH_UNSUPPORTED);
    CHECK(g_fake.calls.size() == 1);
    g_family = Family::Meta;
}

}  // namespace

int main() {
    Setup();
    AcceptedTableIsGivenOnce();
    OwnProfileRefusedWithPathUnsupportedGivesTheOthers();
    OtherProfileRefusedWithPathUnsupportedIsLeftAlone();
    OtherErrorsStillGoOneByOneAsBefore();
    NothingAcceptedKeepsTheRefusal();
    ASingleBindingIsNotTriedAgain();
    AWholeRefusalWithNoBadBindingKeepsTheRefusal();
    WithoutAKnownFamilyNothingChanges();
    if (g_failures == 0) std::printf("all checks passed\n");
    return g_failures == 0 ? 0 : 1;
}
