// How the layer scales the strength of a controller's vibration for a game (the player's choice, GAMEPORT_XR_HAPTICS). Pure logic, no OpenXR, so it is tested on the
// host (scripts/test_xr_layer.sh).
#pragma once

#include <algorithm>
#include <cstdlib>

namespace gp {

// The percentage the player chose, read from the text the hook puts in the environment: a whole number from 0 to 300. Anything else (nothing, text, out of range)
// means the game's own strength, 100.
inline int ParseHapticPercent(const char* text) {
    if (!text || !text[0]) return 100;
    char* end = nullptr;
    const long value = std::strtol(text, &end, 10);
    if (end == text || *end != '\0' || value < 0 || value > 300) return 100;
    return static_cast<int>(value);
}

// The amplitude a game asked for (0 to 1), scaled by the percentage and kept within 0 to 1.
inline float ScaleHapticAmplitude(float amplitude, int percent) {
    return std::min(1.0f, std::max(0.0f, amplitude * (static_cast<float>(percent) / 100.0f)));
}

}  // namespace gp
