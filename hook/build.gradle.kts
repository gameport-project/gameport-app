plugins {
    alias(libs.plugins.gameport.jvm.library)
}

// The logic of the in-game hook. It is plain Java without Android classes so it can be tested on
// a computer; the thin Android glue lives in src/android and is compiled together with this code
// into the dex that patched games carry (scripts/dev/build_hook.sh).
dependencies {
    testImplementation(libs.junit)
}
