plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.sync"
}

dependencies {
    implementation(project(":core:settings"))
    implementation(project(":core:sync"))
}
