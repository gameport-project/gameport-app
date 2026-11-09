plugins {
    alias(libs.plugins.gameport.android.library)
    alias(libs.plugins.gameport.android.hilt)
}

android {
    namespace = "app.gameport.core.settings"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:device"))
    implementation(libs.kotlinx.coroutines.core)
}
