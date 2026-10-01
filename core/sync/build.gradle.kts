plugins {
    alias(libs.plugins.gameport.android.library)
    alias(libs.plugins.gameport.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.gameport.core.sync"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:device"))
    implementation(project(":core:install"))
    implementation(project(":core:settings"))
    implementation(project(":core:steam"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
