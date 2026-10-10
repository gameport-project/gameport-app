// Where the controls of the Steam Frame's controllers go on the controllers a device has. Pure logic, no OpenXR, so it is tested on the host
// (scripts/test_xr_layer.sh). The app shows the same defaults on its controller page: ControllerLayout in core/model/ControllerMapping.kt
// does the same; keep the two in step.
//
// A control is written "hand:group" ("left:dpad_up", "right:a"). The Steam Frame has, on the right: a, b, x, y, menu, bumper, trigger, squeeze,
// thumbstick; on the left: dpad (up, down, left, right), view, bumper, trigger, squeeze, thumbstick. The controllers of a Meta device have
// trigger, squeeze, thumbstick and thumbrest on both hands, x, y and menu on the left, a and b on the right. Far fewer controls: several of the
// Frame's must share one, or go without. What a game uses decides: only the controls it binds compete.
#pragma once

#include <algorithm>
#include <map>
#include <set>
#include <string>
#include <vector>

namespace gp {

using Targets = std::vector<std::string>;

// Meta and Pico controllers share this layout.
inline bool DeviceHasControl(const std::string& hand, const std::string& group) {
    static const char* both[] = {"trigger", "squeeze", "thumbstick", "thumbrest", "grip", "aim"};
    for (const char* g : both) if (group == g) return true;
    if (hand == "left") return group == "x" || group == "y" || group == "menu";
    return group == "a" || group == "b";
}

inline bool SplitKey(const std::string& key, std::string& hand, std::string& group) {
    const size_t colon = key.find(':');
    if (colon == std::string::npos || colon == 0 || colon + 1 >= key.size()) return false;
    hand = key.substr(0, colon);
    group = key.substr(colon + 1);
    return hand == "left" || hand == "right";
}

// "left:dpad_up=left:y+left:x;right:menu=none" -> by source, the controls it goes to ("none": nowhere). A bad entry is ignored.
inline std::map<std::string, Targets> ParseOverrides(const char* raw) {
    std::map<std::string, Targets> overrides;
    if (!raw) return overrides;
    const std::string all = raw;
    size_t start = 0;
    while (start <= all.size()) {
        size_t end = all.find(';', start);
        if (end == std::string::npos) end = all.size();
        const std::string entry = all.substr(start, end - start);
        const size_t eq = entry.find('=');
        std::string hand, group;
        if (eq != std::string::npos && SplitKey(entry.substr(0, eq), hand, group)) {
            Targets targets;
            const std::string value = entry.substr(eq + 1);
            size_t from = 0;
            while (from <= value.size()) {
                size_t plus = value.find('+', from);
                if (plus == std::string::npos) plus = value.size();
                const std::string target = value.substr(from, plus - from);
                if (SplitKey(target, hand, group) && std::find(targets.begin(), targets.end(), target) == targets.end()) targets.push_back(target);
                from = plus + 1;
            }
            overrides[entry.substr(0, eq)] = targets;
        }
        start = end + 1;
    }
    return overrides;
}

// The controls a Frame control falls back on, in order of preference, when the device has no control of that name.
inline const Targets& Fallbacks(const std::string& source) {
    static const std::map<std::string, Targets> table = {
        {"right:x", {"left:x", "left:y"}},
        {"right:y", {"left:y", "left:x"}},
        {"right:menu", {"left:menu"}},
        // Valve maps the Touch's top button onto the Frame's left, up and right and its bottom one onto down; the other way round, by what the game uses.
        {"left:dpad_up", {"left:y", "left:x"}},
        {"left:dpad_down", {"left:x", "left:y"}},
        {"left:dpad_left", {"left:x", "left:y"}},
        {"left:dpad_right", {"left:y", "left:x"}},
        {"left:bumper", {"left:squeeze"}},
        {"right:bumper", {"right:squeeze"}},
    };
    static const Targets none;
    auto it = table.find(source);
    return it == table.end() ? none : it->second;
}

// The order in which the fallbacks are given out: the face buttons first, then the D-pad, the bumpers and the rest.
inline const std::vector<std::string>& FallbackOrder() {
    static const std::vector<std::string> order = {"right:x", "right:y", "left:dpad_up", "left:dpad_down", "left:dpad_left", "left:dpad_right",
                                                   "right:menu", "left:bumper", "right:bumper"};
    return order;
}

// For each control in [used] (the controls the game binds), where it goes. A control the player chose for ([overrides], when [useOverrides]) goes
// there. Else it keeps its name when the device has it; else it takes the first of its fallbacks that no other control has taken, and when they
// are all taken it shares the first one. A control with no fallback (view, the extra ones) goes nowhere.
inline std::map<std::string, Targets> Allocate(const std::set<std::string>& used, const std::map<std::string, Targets>& overrides, bool useOverrides) {
    std::map<std::string, Targets> result;
    std::set<std::string> taken;
    if (useOverrides) {
        for (const std::string& source : used) {
            auto it = overrides.find(source);
            if (it == overrides.end()) continue;
            result[source] = it->second;
            taken.insert(it->second.begin(), it->second.end());
        }
    }
    for (const std::string& source : used) {
        if (result.count(source)) continue;
        std::string hand, group;
        if (SplitKey(source, hand, group) && DeviceHasControl(hand, group)) {
            result[source] = {source};
            taken.insert(source);
        }
    }
    for (const std::string& source : FallbackOrder()) {
        if (!used.count(source) || result.count(source)) continue;
        const Targets& candidates = Fallbacks(source);
        auto free = std::find_if(candidates.begin(), candidates.end(), [&](const std::string& c) { return !taken.count(c); });
        if (free != candidates.end()) {
            result[source] = {*free};
            taken.insert(*free);
        } else {
            result[source] = {candidates.front()};
        }
    }
    for (const std::string& source : used) if (!result.count(source)) result[source] = {};
    return result;
}

// The controls of the device that receive more than one control of the game: pressing one fires every action bound to the others too.
inline std::map<std::string, std::vector<std::string>> SharedTargets(const std::map<std::string, Targets>& allocation) {
    std::map<std::string, std::vector<std::string>> by;
    for (const auto& [source, targets] : allocation) for (const std::string& target : targets) by[target].push_back(source);
    for (auto it = by.begin(); it != by.end();) it = it->second.size() < 2 ? by.erase(it) : std::next(it);
    return by;
}

// The part of an input path the device offers for the part a game bound on a Steam Frame control ("click", "value", "touch", "x", "pose"...). [sub] is empty when
// the game binds the control itself ("/input/trigger", "/input/a"): the Steam Frame lets it, and a game of Unreal does it for its triggers; the device's
// controller has no such path, so it is its main value ("value" for a trigger or a squeeze, "click" for a button). True with [out] set, or false when the device has
// nothing for it. A float serves a boolean action (the runtime thresholds it).
inline bool DeviceSubFor(const std::string& group, const std::string& sub, bool pico, std::string& out) {
    auto pick = [&](const char* value) { out = value; return true; };
    if (group == "trigger") {
        if (sub.empty() || sub == "value") return pick("value");
        if (sub == "click") return pick(pico ? "click" : "value");
        if (sub == "touch") return pick("touch");
        return false;
    }
    if (group == "squeeze") {
        if (sub.empty() || sub == "value") return pick("value");
        return sub == "click" ? pick(pico ? "click" : "value") : false;
    }
    if (group == "thumbstick") {
        if (sub.empty()) return pick("");
        return (sub == "x" || sub == "y" || sub == "click" || sub == "touch") ? pick(sub.c_str()) : false;
    }
    if (group == "thumbrest") return sub == "touch" ? pick("touch") : false;
    if (group == "grip" || group == "aim") return sub == "pose" ? pick("pose") : false;
    // Buttons: a, b, x, y, menu.
    if (sub.empty() || sub == "click" || sub == "value") return pick("click");
    if (sub == "touch" && group != "menu") return pick("touch");
    return false;
}

// The stage of a headset that has no play area is a small fixed square (about 1.09 m a side on a Quest in stationary mode) that recentering does not move, or
// no bounds at all. A real play area is larger. [available]: the runtime gave bounds; [width], [depth] in metres.
inline bool StageLooksLikeFallback(bool available, float width, float depth) { return !available || (width <= 1.2f && depth <= 1.2f); }

// A game's actions answer "active" when they are bound to a control that is there, pressed or not. When the game keeps asking for half a minute and none ever
// answers active, its controls are not bound to the controllers in use: the game cannot hear them. [polls]: answers seen, [active]: those that said active,
// [elapsedMs]: since the first one. Few answers say nothing (a game in a loading screen asks little or nothing).
inline bool ActionsLookSilent(unsigned polls, unsigned active, long long elapsedMs) { return polls >= 200 && active == 0 && elapsedMs >= 30000; }

// Whether an input path belongs to the Steam Frame's controllers only: the controllers of a Meta device have no D-pad, no bumper and no view button.
// A table whose profile name could not be read is recognised as the Steam Frame's by these.
inline bool IsFrameControlPath(const std::string& path) {
    return path.find("/input/dpad_") != std::string::npos || path.find("/input/bumper") != std::string::npos || path.find("/input/view") != std::string::npos;
}

// How the actions of a game's Steam Frame table compare with those of its tables for the device's own controllers. Measured on Unity and Unreal games:
// the actions are made for each profile (few are shared), so what only the Steam Frame's table has is not missing on the device's.
template <typename A>
struct Gaps {
    std::set<A> lacking;    // actions of the Steam Frame's table that no table of the device's controllers has
    size_t shared = 0;      // actions the Steam Frame's table has in common with the device's tables
    bool separate = false;  // they have no action in common
};

template <typename A>
Gaps<A> FindGaps(const std::set<A>& frame, const std::vector<std::set<A>>& natives) {
    Gaps<A> gaps;
    std::set<A> all;
    for (const auto& native : natives) all.insert(native.begin(), native.end());
    for (const A& action : frame) {
        if (all.count(action)) gaps.shared++;
        else gaps.lacking.insert(action);
    }
    if (gaps.shared == 0) {
        gaps.separate = true;
        gaps.lacking.clear();
    }
    return gaps;
}

}  // namespace gp
