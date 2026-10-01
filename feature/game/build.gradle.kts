plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.game"
}

dependencies {
    implementation(project(":core:settings"))
    implementation(project(":core:install"))
    implementation(project(":core:device"))
    implementation(project(":core:sync"))
}
