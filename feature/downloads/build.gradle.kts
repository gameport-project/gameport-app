plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.downloads"
}

dependencies {
    implementation(project(":core:settings"))
    implementation(project(":core:install"))
}
