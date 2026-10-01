plugins {
    alias(libs.plugins.gameport.android.library)
    alias(libs.plugins.gameport.android.hilt)
}

android {
    namespace = "app.gameport.core.device"
}

dependencies {
    api(project(":core:model"))
}
