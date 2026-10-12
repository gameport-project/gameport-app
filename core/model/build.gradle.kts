plugins {
    alias(libs.plugins.gameport.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// The tests read the release files that the app ships: a change there must run them again.
tasks.withType<Test>().configureEach {
    inputs.dir("../../app/src/main/assets/releases")
}
