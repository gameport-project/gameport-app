// Host test of controller_allocation.h: scripts/test_xr_layer.sh. No framework; a failed check prints and the exit code is 1.
#include <cstdio>
#include <cstdlib>

#include "../controller_allocation.h"

static int failures = 0;
#define CHECK(cond) do { if (!(cond)) { std::printf("FAILED line %d: %s\n", __LINE__, #cond); failures++; } } while (0)

using gp::Targets;

static Targets T(std::initializer_list<const char*> items) { return Targets(items.begin(), items.end()); }
static std::map<std::string, Targets> none() { return {}; }

int main() {
    // A game made for a Quest and ported: the D-pad's up and down stand for Y and X.
    {
        auto a = gp::Allocate({"left:dpad_up", "left:dpad_down", "right:a", "right:b", "left:thumbstick"}, none(), false);
        CHECK(a["left:dpad_up"] == T({"left:y"}));
        CHECK(a["left:dpad_down"] == T({"left:x"}));
        CHECK(a["right:a"] == T({"right:a"}));
        CHECK(a["left:thumbstick"] == T({"left:thumbstick"}));
        CHECK(gp::SharedTargets(a).empty());
    }
    // A game that uses the D-pad's left and right: left is X and right is Y.
    {
        auto a = gp::Allocate({"left:dpad_left", "left:dpad_right"}, none(), false);
        CHECK(a["left:dpad_left"] == T({"left:x"}));
        CHECK(a["left:dpad_right"] == T({"left:y"}));
        CHECK(gp::SharedTargets(a).empty());
    }
    // All four directions: up and down take Y and X, left and right share them.
    {
        auto a = gp::Allocate({"left:dpad_up", "left:dpad_down", "left:dpad_left", "left:dpad_right"}, none(), false);
        CHECK(a["left:dpad_up"] == T({"left:y"}));
        CHECK(a["left:dpad_down"] == T({"left:x"}));
        CHECK(a["left:dpad_left"] == T({"left:x"}));
        CHECK(a["left:dpad_right"] == T({"left:y"}));
        auto shared = gp::SharedTargets(a);
        CHECK(shared.size() == 2 && shared["left:x"].size() == 2 && shared["left:y"].size() == 2);
    }
    // The four face buttons of the Frame: each gets its own button, the right-hand ones crossing to the left hand.
    {
        auto a = gp::Allocate({"right:a", "right:b", "right:x", "right:y"}, none(), false);
        CHECK(a["right:a"] == T({"right:a"}));
        CHECK(a["right:b"] == T({"right:b"}));
        CHECK(a["right:x"] == T({"left:x"}));
        CHECK(a["right:y"] == T({"left:y"}));
        CHECK(gp::SharedTargets(a).empty());
    }
    // Face buttons and D-pad together: the face buttons take X and Y first, the D-pad shares.
    {
        auto a = gp::Allocate({"right:x", "right:y", "left:dpad_up", "left:dpad_down"}, none(), false);
        CHECK(a["right:x"] == T({"left:x"}));
        CHECK(a["right:y"] == T({"left:y"}));
        CHECK(a["left:dpad_up"] == T({"left:y"}));
        CHECK(a["left:dpad_down"] == T({"left:x"}));
        CHECK(gp::SharedTargets(a).size() == 2);
    }
    // Bumpers go to the squeeze of the same hand, and share it with the grip when the game binds both.
    {
        auto a = gp::Allocate({"left:bumper", "right:bumper"}, none(), false);
        CHECK(a["left:bumper"] == T({"left:squeeze"}));
        CHECK(a["right:bumper"] == T({"right:squeeze"}));
        auto b = gp::Allocate({"right:bumper", "right:squeeze"}, none(), false);
        CHECK(b["right:squeeze"] == T({"right:squeeze"}));
        CHECK(b["right:bumper"] == T({"right:squeeze"}));
        CHECK(gp::SharedTargets(b)["right:squeeze"].size() == 2);
    }
    // The right menu goes to the left menu; the view buttons and unknown controls go nowhere.
    {
        auto a = gp::Allocate({"right:menu", "left:view", "right:view", "left:steam"}, none(), false);
        CHECK(a["right:menu"] == T({"left:menu"}));
        CHECK(a["left:view"].empty() && a["right:view"].empty() && a["left:steam"].empty());
    }
    // A control the game does not use takes nothing: only what is used competes.
    {
        auto a = gp::Allocate({"left:dpad_up"}, none(), false);
        CHECK(a.size() == 1 && a["left:dpad_up"] == T({"left:y"}));
        CHECK(gp::Allocate({}, none(), false).empty());
    }
    // The player's choices: several targets, nowhere, and a choice that frees the default for another control.
    {
        auto o = gp::ParseOverrides("left:dpad_up=left:y+left:x;right:menu=none;bad;=x;left:view=nowhere;right:x=left:x+left:x");
        CHECK(o.size() == 4);
        CHECK(o["left:dpad_up"] == T({"left:y", "left:x"}));
        CHECK(o["right:menu"].empty());
        CHECK(o["left:view"].empty());
        CHECK(o["right:x"] == T({"left:x"}));
        auto a = gp::Allocate({"left:dpad_up", "left:dpad_down", "right:menu"}, o, true);
        CHECK(a["left:dpad_up"] == T({"left:y", "left:x"}));
        CHECK(a["right:menu"].empty());
        // The choice took X and Y: the D-pad's down finds none free and shares X.
        CHECK(a["left:dpad_down"] == T({"left:x"}));
        // Without the player's switch the choices are not used.
        auto d = gp::Allocate({"left:dpad_up", "left:dpad_down", "right:menu"}, o, false);
        CHECK(d["left:dpad_up"] == T({"left:y"}) && d["right:menu"] == T({"left:menu"}));
    }
    // Gaps: a game that makes one set of actions for all profiles (Unreal): what only the Steam Frame's table has is lacking.
    {
        using Set = std::set<int>;
        auto g = gp::FindGaps<int>(Set{1, 2, 3, 4}, {Set{1, 2, 3}, Set{1, 2}});
        CHECK(!g.separate && g.shared == 3 && g.lacking == Set{4});
        // Present on any of the device's tables is enough to count as having a button.
        auto h = gp::FindGaps<int>(Set{1, 2, 3}, {Set{1}, Set{2, 3}});
        CHECK(!h.separate && h.shared == 3 && h.lacking.empty());
        // A game that makes separate actions for each profile (Unity): none in common, so nothing is lacking.
        auto u = gp::FindGaps<int>(Set{10, 11, 12}, {Set{1, 2}, Set{3, 4, 5}});
        CHECK(u.separate && u.shared == 0 && u.lacking.empty());
        // No Steam Frame table, or no device table: nothing to complete.
        CHECK(gp::FindGaps<int>(Set{}, {Set{1}}).lacking.empty());
        CHECK(gp::FindGaps<int>(Set{1, 2}, {}).lacking.empty());
    }
    // A game that binds a trigger or a squeeze itself (not its value, click or touch) gets the device's value, and a button itself its click.
    {
        std::string out;
        CHECK(gp::DeviceSubFor("trigger", "", false, out) && out == "value");
        CHECK(gp::DeviceSubFor("squeeze", "", false, out) && out == "value");
        CHECK(gp::DeviceSubFor("a", "", false, out) && out == "click");
        CHECK(gp::DeviceSubFor("menu", "", false, out) && out == "click");
        CHECK(gp::DeviceSubFor("thumbstick", "", false, out) && out == "");
        // What was translated before keeps its target.
        CHECK(gp::DeviceSubFor("trigger", "click", false, out) && out == "value");
        CHECK(gp::DeviceSubFor("trigger", "click", true, out) && out == "click");
        CHECK(gp::DeviceSubFor("trigger", "touch", false, out) && out == "touch");
        CHECK(gp::DeviceSubFor("squeeze", "value", false, out) && out == "value");
        CHECK(gp::DeviceSubFor("b", "touch", false, out) && out == "touch");
        CHECK(gp::DeviceSubFor("thumbstick", "x", false, out) && out == "x");
        // The device has no touch on a squeeze or a menu button, and no such sub-input as "bogus".
        CHECK(!gp::DeviceSubFor("squeeze", "touch", false, out));
        CHECK(!gp::DeviceSubFor("menu", "touch", false, out));
        CHECK(!gp::DeviceSubFor("trigger", "bogus", false, out));
    }
    // A stage that is only the fallback of a headset without play area: tiny or without bounds. A room is larger.
    CHECK(gp::StageLooksLikeFallback(true, 1.088944f, 1.088944f));
    CHECK(gp::StageLooksLikeFallback(false, 0.0f, 0.0f));
    CHECK(!gp::StageLooksLikeFallback(true, 2.5f, 2.0f));
    CHECK(!gp::StageLooksLikeFallback(true, 1.0f, 3.0f));
    // Silent: half a minute of questions and not one action active. Anything less says nothing.
    CHECK(gp::ActionsLookSilent(5000, 0, 30000));
    CHECK(!gp::ActionsLookSilent(5000, 1, 30000));
    CHECK(!gp::ActionsLookSilent(5000, 0, 29999));
    CHECK(!gp::ActionsLookSilent(199, 0, 120000));
    CHECK(!gp::ActionsLookSilent(0, 0, 0));
    // A table is the Steam Frame's when it binds a control only that controller has.
    CHECK(gp::IsFrameControlPath("/user/hand/left/input/dpad_up/click"));
    CHECK(gp::IsFrameControlPath("/user/hand/right/input/bumper/click"));
    CHECK(gp::IsFrameControlPath("/user/hand/left/input/view/click"));
    CHECK(!gp::IsFrameControlPath("/user/hand/left/input/thumbstick/x"));
    CHECK(!gp::IsFrameControlPath("/user/hand/right/input/a/click"));
    CHECK(!gp::IsFrameControlPath("/user/hand/left/input/squeeze/value"));
    CHECK(gp::ParseOverrides(nullptr).empty());
    CHECK(gp::ParseOverrides("").empty());
    if (failures == 0) std::printf("all checks passed\n");
    return failures == 0 ? 0 : 1;
}
