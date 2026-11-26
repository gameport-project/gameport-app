// Golden test of what the layer does, with the "automatic" choice, when the game attaches its actions (EmulateIfNeeded / EmulateFrom): the calls it makes to the runtime
// (with a fingerprint of every table given) and the lines it logs, in order, for tables shaped like those of real games. The expected transcripts
// (emulate_golden_expected.inc) were recorded from the code as it was before the player could choose the controllers presented to the game, and the four cases of a Pico whose game
// has a Touch table were recorded again when a Pico began to read the Quest's controls of such a game by default (the others did not change): a change that alters them
// changes what a game gets in the automatic mode. Lines the layer adds for the profile summary ("controller profile") are not part of what is compared.
// Run by scripts/test_xr_layer.sh when the OpenXR headers are there; each case runs in its own process. "--print <case>" prints a transcript.
#include "emulate_fixture.h"

namespace {

using namespace fixture;

const std::map<std::string, std::string> kExpected = {
#include "emulate_golden_expected.inc"
};

// The lines the layer writes only to describe the game's profiles are left out of the comparison.
std::string Comparable() {
    std::string out;
    for (const std::string& line : g_transcript) {
        if (line.compare(0, 4, "LOG ") == 0 && line.compare(4, 19, "controller profile:") == 0) continue;
        out += line + "\n";
    }
    return out;
}

void Finish(const char* name) {
    NoteState();
    const std::string got = Comparable();
    if (g_print) {
        std::printf("%s", got.c_str());
        return;
    }
    auto expected = kExpected.find(name);
    if (expected == kExpected.end()) {
        std::printf("no expected transcript for %s\n", name);
        g_failures++;
        return;
    }
    if (got != expected->second) {
        std::printf("FAILED: %s differs from the recorded transcript.\n--- expected\n%s--- got\n%s", name, expected->second.c_str(), got.c_str());
        g_failures++;
    }
}

// Moss: Touch 33 and the Steam Frame's 44 (its own actions); on a Pico the runtime refuses the Frame's translated table (-22).
void AutoPicoMossShapeQuestControls() {
    Reset(Family::Pico);
    Game(kTouch, TouchVocab(), 33, 1000);
    Game(kFrame, FrameVocab(), 44, 2000);
    Refuse(2000, 44);
    EmulateIfNeeded(nullptr);
    Finish("AutoPicoMossShapeQuestControls");
}

// Red Matter 2: Touch 30, Index 8, simple 6, gaze 1, the Steam Frame's 33 (unnamed by the game, taken for the Frame's); the Frame's is accepted.
void AutoPicoRedMatterFrameAccepted() {
    Reset(Family::Pico);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kIndex, IndexVocab(), 8, 3000);
    Game(kSimple, SimpleVocab(), 6, 4000);
    Game(kGaze, GazeVocab(), 1, 5000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    Finish("AutoPicoRedMatterFrameAccepted");
}

void AutoPicoOnlyTouchTranslated() {
    Reset(Family::Pico);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kTouchPlus, TouchVocab(), 33, 1100);
    EmulateIfNeeded(nullptr);
    Finish("AutoPicoOnlyTouchTranslated");
}

void AutoPicoFrameAndTouchBothRefused() {
    Reset(Family::Pico);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    g_rt.refusedProfiles.insert(kPico);
    EmulateIfNeeded(nullptr);
    Finish("AutoPicoFrameAndTouchBothRefused");
}

void AutoMetaTouchPlusFrameNothingTranslated() {
    Reset(Family::Meta);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kIndex, IndexVocab(), 8, 3000);
    Game(kSimple, SimpleVocab(), 6, 4000);
    Game(kGaze, GazeVocab(), 1, 5000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    Finish("AutoMetaTouchPlusFrameNothingTranslated");
}

void AutoMetaMossShapeNothingTranslated() {
    Reset(Family::Meta);
    Game(kTouch, TouchVocab(), 33, 1000);
    Game(kFrame, FrameVocab(), 44, 2000);
    Refuse(2000, 44);
    EmulateIfNeeded(nullptr);
    Finish("AutoMetaMossShapeNothingTranslated");
}

void AutoMetaTouchOnly() {
    Reset(Family::Meta);
    Game(kTouch, TouchVocab(), 30, 1000);
    EmulateIfNeeded(nullptr);
    Finish("AutoMetaTouchOnly");
}

void AutoMetaFrameOnlyTranslatesAtEnd() {
    Reset(Family::Meta);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    Finish("AutoMetaFrameOnlyTranslatesAtEnd");
}

void AutoPicoNativeTablePresent() {
    Reset(Family::Pico);
    Game(kPico, TouchVocab(), 30, 6000);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    Finish("AutoPicoNativeTablePresent");
}

void AutoUnknownFamilyNothing() {
    Reset(Family::Unknown);
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    Finish("AutoUnknownFamilyNothing");
}

// The old variable (GAMEPORT_XR_PREFER_FRAME) still sets the preference: the game's own tables were kept back, the Frame's is translated.
void PreferFrameMetaBothTables() {
    Reset(Family::Meta);
    g_preferFrame = true;
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    EmulateIfNeeded(nullptr);
    Finish("PreferFrameMetaBothTables");
}

void PreferFrameMetaFrameRefusedReplays() {
    Reset(Family::Meta);
    g_preferFrame = true;
    Game(kTouch, TouchVocab(), 30, 1000);
    Game(kFrame, FrameVocab(), 33, 2000);
    Refuse(2000, 33);
    EmulateIfNeeded(nullptr);
    Finish("PreferFrameMetaFrameRefusedReplays");
}

void PreferFrameMetaTouchOnly() {
    Reset(Family::Meta);
    g_preferFrame = true;
    Game(kTouch, TouchVocab(), 30, 1000);
    EmulateIfNeeded(nullptr);
    Finish("PreferFrameMetaTouchOnly");
}

}  // namespace

int main(int argc, char** argv) {
    return fixture::RunCases(argc, argv,
                             {{"AutoPicoMossShapeQuestControls", AutoPicoMossShapeQuestControls},
                              {"AutoPicoRedMatterFrameAccepted", AutoPicoRedMatterFrameAccepted},
                              {"AutoPicoOnlyTouchTranslated", AutoPicoOnlyTouchTranslated},
                              {"AutoPicoFrameAndTouchBothRefused", AutoPicoFrameAndTouchBothRefused},
                              {"AutoMetaTouchPlusFrameNothingTranslated", AutoMetaTouchPlusFrameNothingTranslated},
                              {"AutoMetaMossShapeNothingTranslated", AutoMetaMossShapeNothingTranslated},
                              {"AutoMetaTouchOnly", AutoMetaTouchOnly},
                              {"AutoMetaFrameOnlyTranslatesAtEnd", AutoMetaFrameOnlyTranslatesAtEnd},
                              {"AutoPicoNativeTablePresent", AutoPicoNativeTablePresent},
                              {"AutoUnknownFamilyNothing", AutoUnknownFamilyNothing},
                              {"PreferFrameMetaBothTables", PreferFrameMetaBothTables},
                              {"PreferFrameMetaFrameRefusedReplays", PreferFrameMetaFrameRefusedReplays},
                              {"PreferFrameMetaTouchOnly", PreferFrameMetaTouchOnly}});
}
