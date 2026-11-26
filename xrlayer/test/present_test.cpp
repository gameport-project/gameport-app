// Host test of the controllers the player can choose to present to the game (GAMEPORT_XR_PRESENT: auto, frame, touch) and of the report the layer leaves for GamePort
// (xr_profiles.txt), against a fake runtime. The "automatic" mode itself is held by emulate_golden_test. Each case runs in its own process.
#include "emulate_fixture.h"

#include <unistd.h>

namespace {

using namespace fixture;

std::vector<std::string> g_files;         // what the layer would have written to xr_profiles.txt, each time it writes it
std::vector<std::string> g_controlFiles;  // and to xr_controls.txt (the controls the page is told the game uses)

void Start(Family family, Present present) {
    Reset(family);
    g_files.clear();
    g_controlFiles.clear();
    g_writeProfilesText = [](const std::string& text) { g_files.push_back(text); };
    g_writeControlsText = [](const std::string& text) { g_controlFiles.push_back(text); };
    g_present = static_cast<int>(present);
    // What the layer's start does for "frame": the existing preference.
    g_preferFrame = present == Present::Frame;
}

bool FileHas(const std::string& line) { return !g_files.empty() && g_files.back().find(line + "\n") != std::string::npos; }
int Summaries() { return CountLogs("controller profile: /"); }

void PresentInvalidValueIsAuto() {
    bool unknown = false;
    CHECK(ParsePresent(nullptr, &unknown) == Present::Auto && !unknown);
    CHECK(ParsePresent("", &unknown) == Present::Auto && !unknown);
    CHECK(ParsePresent("auto", &unknown) == Present::Auto && !unknown);
    CHECK(ParsePresent("frame", &unknown) == Present::Frame && !unknown);
    CHECK(ParsePresent("touch", &unknown) == Present::Touch && !unknown);
    CHECK(ParsePresent("Touch", &unknown) == Present::Auto && unknown);
    CHECK(ParsePresent("index", &unknown) == Present::Auto && unknown);
    CHECK(ParsePresent("frame ", &unknown) == Present::Auto && unknown);
    CHECK(ParsePresent("1", nullptr) == Present::Auto);
}

void PresentUnknownFamilyIgnored() {
    Start(Family::Unknown, Present::Touch);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(Suggestions("") == 0);
    CHECK(!g_emulating.load());
    CHECK(FileHas("presented=ignored"));
    CHECK(FileHas("used=none"));
}

void PresentFrameUnknownFamilyIsIgnored() {
    Start(Family::Unknown, Present::Frame);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(Suggestions("") == 0);
    CHECK(FileHas("present=frame") && FileHas("presented=ignored") && FileHas("used=none"));
}

void PresentTouchUnknownFamilyIsIgnored() {
    Start(Family::Unknown, Present::Touch);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(Suggestions("") == 0);
    CHECK(FileHas("present=touch") && FileHas("presented=ignored") && FileHas("used=none"));
}

std::string ReadAll(const std::string& path) {
    std::string text;
    FILE* in = fopen(path.c_str(), "r");
    if (!in) return "<missing>";
    char buffer[256];
    size_t n;
    while ((n = fread(buffer, 1, sizeof buffer, in)) > 0) text.append(buffer, n);
    fclose(in);
    return text;
}

void ProfilesFileIsWrittenAtomically() {
    char pattern[] = "/tmp/gp_present_XXXXXX";
    const char* dir = mkdtemp(pattern);
    CHECK(dir != nullptr);
    const std::string folder = std::string(dir) + "/";
    const std::string path = folder + "xr_profiles.txt";
    CHECK(WriteFileAtomically(path, "present=auto\n"));
    CHECK(ReadAll(path) == "present=auto\n");
    CHECK(WriteFileAtomically(path, "present=frame\n"));
    CHECK(ReadAll(path) == "present=frame\n");
    // No temporary file is left next to it.
    CHECK(ReadAll(path + ".tmp") == "<missing>");
    // A folder that does not exist: false, nothing created.
    CHECK(!WriteFileAtomically(folder + "absent/xr_profiles.txt", "x"));
    // The start of a new run removes the file of the last one (and does nothing when there is none).
    RemoveStaleProfilesText(folder);
    CHECK(ReadAll(path) == "<missing>");
    RemoveStaleProfilesText(folder);
    RemoveStaleProfilesText("");
    rmdir(dir);
}

void PresentFrameMetaBothTablesUsesPreferPath() {
    Start(Family::Meta, Present::Frame);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(Has("the game uses the Steam Frame's controls, as the player asked"));
    CHECK(g_emulating.load() && g_rt.names[g_emulatedSourcePath] == kFrame);
    CHECK(FileHas("present=frame") && FileHas("presented=applied") && FileHas("used=frame"));
}

void PresentFramePicoNoNativeKeepsTheFrame() {
    Start(Family::Pico, Present::Frame);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(g_rt.names[g_emulatedSourcePath] == kFrame);
    CHECK(Has("they are translated onto this device's controllers, as the player asked"));
    CHECK(!Has("touch controls translated"));
    CHECK(FileHas("presented=applied") && FileHas("used=frame"));
}

void PresentTouchPicoTranslatesTouchBeforeFrame() {
    Start(Family::Pico, Present::Touch);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(g_emulating.load() && g_rt.names[g_emulatedSourcePath] == kTouch);
    CHECK(Has("touch controls translated with the overrides"));
    CHECK(!Has("valve controls translated"));
    CHECK(Has("the game uses the Touch controls, as the player asked"));
    CHECK(FileHas("present=touch") && FileHas("presented=applied") && FileHas("used=touch"));
    CHECK(Has("controller profile: present=touch presented=applied used=touch"));
}

void PresentTouchPicoWithoutTouchTableFallsBackToAuto() {
    Start(Family::Pico, Present::Touch);
    Game(kIndex, IndexVocab(), 8, 3000);
    Game(kSimple, SimpleVocab(), 6, 4000);
    EmulateIfNeeded(nullptr);
    CHECK(Suggestions("") == 0);
    CHECK(!g_emulating.load());
    CHECK(Has("the game has no table for them: the automatic choice is used"));
    CHECK(FileHas("presented=fallback") && FileHas("used=none"));
}

void PresentTouchMetaDoesNothing() {
    Start(Family::Meta, Present::Touch);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(Suggestions("") == 0);
    CHECK(CountLogs("the player chose the Touch controls: this device has no use for that choice") == 1);
    CHECK(FileHas("presented=ignored") && FileHas("used=native"));
}

void PresentChosenRefusedMinus22RestoresAndFallsBack() {
    Start(Family::Pico, Present::Touch);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    Refuse(1000, 30);
    EmulateIfNeeded(nullptr);
    CHECK(Has("touch controls translated with the defaults only"));
    CHECK(Has("the Touch controls could not be translated"));
    // The automatic path went on: the Steam Frame's table is the one used.
    CHECK(g_emulating.load() && g_rt.names[g_emulatedSourcePath] == kFrame);
    CHECK(FileHas("presented=fallback") && FileHas("used=frame"));
}

void PresentTouchPicoOnlyFrameGameFallsBack() {
    Start(Family::Pico, Present::Touch);
    Game(kFrame, FrameVocab(), 44, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(g_emulating.load() && g_rt.names[g_emulatedSourcePath] == kFrame);
    CHECK(Has("the game has no table for them"));
    CHECK(FileHas("presented=fallback") && FileHas("used=frame"));
}

void PresentTouchPicoNativeTableIgnored() {
    Start(Family::Pico, Present::Touch);
    Game(kPico, TouchVocab(), 30, 6000);
    Game(kTouch, TouchVocab(), 30, 1000);
    EmulateIfNeeded(nullptr);
    CHECK(Suggestions("") == 0);
    CHECK(FileHas("presented=ignored") && FileHas("used=native"));
}

void PresentTwiceAttachNoDuplicateLogs() {
    Start(Family::Pico, Present::Touch);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kIndex, IndexVocab(), 8, 3000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    const int summaries = Summaries();
    EmulateIfNeeded(nullptr);
    CHECK(summaries == 2);  // the Index's and the Steam Frame's: the translated Touch's has its own detail
    CHECK(Summaries() == summaries);
    CHECK(CountLogs("controller profile: present=") == 1);
    CHECK(CountLogs("the game uses the Touch controls, as the player asked") == 1);
    CHECK(g_files.size() == 1);
}

void ProfilesFileWrittenOnceAndListsAllTables() {
    Start(Family::Pico, Present::Auto);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kIndex, IndexVocab(), 8, 3000);
    Game(kSimple, SimpleVocab(), 6, 4000);
    Game(kGaze, GazeVocab(), 1, 5000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    EmulateIfNeeded(nullptr);
    CHECK(g_files.size() == 1);
    std::vector<std::string> names = {kGaze, kSimple, kTouch, kFrame, kIndex};
    std::sort(names.begin(), names.end());
    std::string expected;
    const std::map<std::string, int> counts = {{kGaze, 1}, {kSimple, 6}, {kTouch, 30}, {kFrame, 33}, {kIndex, 8}};
    for (const std::string& n : names) expected += "profile=" + n + "|" + std::to_string(counts.at(n)) + "\n";
    // Nobody chose: a Pico reads the Quest's controls of a game that has them, so the table in use is the Touch's, with the controls it binds (sorted).
    expected += "present=auto\npresented=applied\nused=touch\ntable=" + std::string(kTouch) + "\n";
    for (const char* control : {"left:menu", "left:squeeze", "left:thumbstick", "left:trigger", "left:x", "left:y", "right:a", "right:b", "right:squeeze", "right:thumbstick", "right:trigger"}) {
        expected += std::string("control=") + control + "\n";
    }
    CHECK(g_files.size() == 1 && g_files[0] == expected);
    CHECK(Summaries() == 4);
}

void AutoPicoUsesTheQuestControlsOfAGameThatHasThem() {
    Start(Family::Pico, Present::Auto);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(g_emulating.load() && g_rt.names[g_emulatedSourcePath] == kTouch);
    CHECK(Has("the game has the Quest's controls and this device has none of its own for them: they are used, as on a Quest"));
    CHECK(Has("touch controls translated with the overrides"));
    CHECK(!Has("valve controls translated"));
    CHECK(!Has("as the player asked"));
    CHECK(FileHas("present=auto") && FileHas("presented=applied") && FileHas("used=touch"));
}

void AutoPicoQuestControlsRefusedFallsBackToTheFrame() {
    Start(Family::Pico, Present::Auto);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    Refuse(1000, 30);
    EmulateIfNeeded(nullptr);
    CHECK(Has("the Quest's controls could not be translated: the game's tables are given back to the runtime and the Steam Frame's are tried"));
    CHECK(g_rt.names[g_emulatedSourcePath] == kFrame);
    CHECK(FileHas("used=frame"));
    CHECK(!FileHas("table="));
    // The page is told the table that got used, not the one tried first.
    CHECK(!g_controlFiles.empty() && g_controlFiles.back().compare(0, 13, "source=valve\n") == 0);
}

// Whatever the player chose, a game that has the Quest's controls and the Steam Frame's is told to the page as having both: choosing one must not take the choice of the other away.
void ChoiceDoesNotTakeAwayThatTheGameHasBothTables() {
    for (Present present : {Present::Auto, Present::Frame, Present::Touch}) {
        Start(Family::Pico, present);
        Game(kTouch, TouchVocab(), 30, 1000);
        Game(kFrame, FrameVocab(), 33, 2000);
        EmulateIfNeeded(nullptr);
        CHECK(!g_controlFiles.empty() && g_controlFiles.back().find("both=1\n") != std::string::npos);
    }
    // A game with the Steam Frame's controls only is not told as having both.
    Start(Family::Pico, Present::Frame);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(!g_controlFiles.empty() && g_controlFiles.back().find("both=1\n") == std::string::npos);
}

void AutoPicoPreferFrameEnvKeepsTheFrame() {
    Start(Family::Pico, Present::Auto);
    g_preferFrame = true;  // GAMEPORT_XR_PREFER_FRAME=1 with no explicit choice
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(g_rt.names[g_emulatedSourcePath] == kFrame);
    CHECK(!Has("the game has the Quest's controls"));
    CHECK(FileHas("used=frame"));
}

// A game with the Steam Frame's own actions (more than its Touch table has): the Quest's controls are still what it reads on a Pico, the choice being to read what a Quest reads. The
// page is told the game has both tables, so that the player may choose the Steam Frame's.
void AutoPicoGameWithOwnFrameActionsStillUsesTheQuestControls() {
    Start(Family::Pico, Present::Auto);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 44, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(g_rt.names[g_emulatedSourcePath] == kTouch);
    CHECK(FileHas("used=touch"));
    CHECK(!g_controlFiles.empty() && g_controlFiles.back().compare(0, 13, "source=touch\n") == 0);
    CHECK(!g_controlFiles.empty() && g_controlFiles.back().find("both=1\n") != std::string::npos);
}

void AutoPicoGameWithOnlyTheFrameKeepsTheFrame() {
    Start(Family::Pico, Present::Auto);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    CHECK(g_rt.names[g_emulatedSourcePath] == kFrame);
    CHECK(!Has("the game has the Quest's controls"));
    CHECK(FileHas("used=frame") && !FileHas("table="));
}

void UsedReflectsTouchWhenFrameFailsThenTouchWorks() {
    Start(Family::Pico, Present::Auto);
    Game(kTouch, TouchVocab(), 33, 1000);
    Game(kFrame, FrameVocab(), 44, 2000);
    Refuse(2000, 44);
    EmulateIfNeeded(nullptr);
    CHECK(g_rt.names[g_emulatedSourcePath] == kTouch);
    CHECK(FileHas("presented=applied") && FileHas("used=touch"));
}

void NothingToReportWritesTheChoiceOnly() {
    Start(Family::Meta, Present::Auto);
    EmulateIfNeeded(nullptr);
    CHECK(g_files.size() == 1 && g_files[0] == "present=auto\npresented=applied\nused=none\n");
}

void ReportedNamesAreSafe() {
    CHECK(ReportableProfile("/interaction_profiles/valve/index_controller"));
    CHECK(!ReportableProfile(""));
    CHECK(!ReportableProfile("/a|b"));
    CHECK(!ReportableProfile("/a:b"));
    CHECK(!ReportableProfile("/a b"));
    CHECK(!ReportableProfile("/a\nb"));
    CHECK(!ReportableProfile(std::string(200, 'x')));
}

}  // namespace

int main(int argc, char** argv) {
    return fixture::RunCases(argc, argv,
                             {{"PresentInvalidValueIsAuto", PresentInvalidValueIsAuto},
                              {"PresentUnknownFamilyIgnored", PresentUnknownFamilyIgnored},
                              {"PresentFrameUnknownFamilyIsIgnored", PresentFrameUnknownFamilyIsIgnored},
                              {"PresentTouchUnknownFamilyIsIgnored", PresentTouchUnknownFamilyIsIgnored},
                              {"ProfilesFileIsWrittenAtomically", ProfilesFileIsWrittenAtomically},
                              {"PresentFrameMetaBothTablesUsesPreferPath", PresentFrameMetaBothTablesUsesPreferPath},
                              {"PresentFramePicoNoNativeKeepsTheFrame", PresentFramePicoNoNativeKeepsTheFrame},
                              {"AutoPicoUsesTheQuestControlsOfAGameThatHasThem", AutoPicoUsesTheQuestControlsOfAGameThatHasThem},
                              {"AutoPicoQuestControlsRefusedFallsBackToTheFrame", AutoPicoQuestControlsRefusedFallsBackToTheFrame},
                              {"AutoPicoGameWithOnlyTheFrameKeepsTheFrame", AutoPicoGameWithOnlyTheFrameKeepsTheFrame},
                              {"ChoiceDoesNotTakeAwayThatTheGameHasBothTables", ChoiceDoesNotTakeAwayThatTheGameHasBothTables},
                              {"AutoPicoPreferFrameEnvKeepsTheFrame", AutoPicoPreferFrameEnvKeepsTheFrame},
                              {"AutoPicoGameWithOwnFrameActionsStillUsesTheQuestControls", AutoPicoGameWithOwnFrameActionsStillUsesTheQuestControls},
                              {"PresentTouchPicoTranslatesTouchBeforeFrame", PresentTouchPicoTranslatesTouchBeforeFrame},
                              {"PresentTouchPicoWithoutTouchTableFallsBackToAuto", PresentTouchPicoWithoutTouchTableFallsBackToAuto},
                              {"PresentTouchMetaDoesNothing", PresentTouchMetaDoesNothing},
                              {"PresentChosenRefusedMinus22RestoresAndFallsBack", PresentChosenRefusedMinus22RestoresAndFallsBack},
                              {"PresentTouchPicoOnlyFrameGameFallsBack", PresentTouchPicoOnlyFrameGameFallsBack},
                              {"PresentTouchPicoNativeTableIgnored", PresentTouchPicoNativeTableIgnored},
                              {"PresentTwiceAttachNoDuplicateLogs", PresentTwiceAttachNoDuplicateLogs},
                              {"ProfilesFileWrittenOnceAndListsAllTables", ProfilesFileWrittenOnceAndListsAllTables},
                              {"UsedReflectsTouchWhenFrameFailsThenTouchWorks", UsedReflectsTouchWhenFrameFailsThenTouchWorks},
                              {"NothingToReportWritesTheChoiceOnly", NothingToReportWritesTheChoiceOnly},
                              {"ReportedNamesAreSafe", ReportedNamesAreSafe}});
}
