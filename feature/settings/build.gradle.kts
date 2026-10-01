plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.settings"
}

dependencies {
    implementation(project(":core:device"))
    implementation(project(":core:install"))
    implementation(project(":core:settings"))
    implementation(project(":core:sync"))
}
