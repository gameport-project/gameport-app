// Host test of how the layer scales a controller's vibration (xrlayer/haptic_scale.h). Run by scripts/test_xr_layer.sh.
#include <cstdio>

#include "../haptic_scale.h"

namespace {

int g_failures = 0;
#define CHECK(condition)                                                                 \
    do {                                                                                 \
        if (!(condition)) {                                                              \
            std::printf("FAILED: %s (line %d)\n", #condition, __LINE__);                 \
            g_failures++;                                                                \
        }                                                                                \
    } while (0)

bool Near(float a, float b) { return a - b < 0.0001f && b - a < 0.0001f; }

}  // namespace

int main() {
    // What the player chose reaches the layer as text.
    CHECK(gp::ParseHapticPercent("50") == 50);
    CHECK(gp::ParseHapticPercent("150") == 150);
    CHECK(gp::ParseHapticPercent("0") == 0);
    // Nothing usable means the game's own strength.
    CHECK(gp::ParseHapticPercent(nullptr) == 100);
    CHECK(gp::ParseHapticPercent("") == 100);
    CHECK(gp::ParseHapticPercent("loud") == 100);
    CHECK(gp::ParseHapticPercent("50%") == 100);
    CHECK(gp::ParseHapticPercent("-10") == 100);
    CHECK(gp::ParseHapticPercent("5000") == 100);

    // The amplitude follows the percentage and never leaves 0 to 1.
    CHECK(Near(gp::ScaleHapticAmplitude(0.6f, 100), 0.6f));
    CHECK(Near(gp::ScaleHapticAmplitude(0.6f, 50), 0.3f));
    CHECK(Near(gp::ScaleHapticAmplitude(0.4f, 150), 0.6f));
    CHECK(Near(gp::ScaleHapticAmplitude(0.9f, 150), 1.0f));
    CHECK(Near(gp::ScaleHapticAmplitude(0.9f, 0), 0.0f));
    CHECK(Near(gp::ScaleHapticAmplitude(-0.5f, 100), 0.0f));

    if (g_failures == 0) std::printf("haptic_scale_test: all checks passed\n");
    return g_failures == 0 ? 0 : 1;
}
