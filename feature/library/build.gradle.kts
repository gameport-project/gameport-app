plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.library"
}

dependencies {
    implementation(project(":core:device"))
    implementation(project(":core:install"))
    implementation(project(":core:sync"))
    implementation(project(":core:settings"))
}
