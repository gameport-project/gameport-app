plugins {
    alias(libs.plugins.gameport.android.library)
    alias(libs.plugins.gameport.android.compose)
}

android {
    namespace = "app.gameport.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.coil.compose)
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.network.okhttp)
}
