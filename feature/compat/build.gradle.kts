plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.compat"
}

dependencies {
    implementation(project(":core:device"))
    implementation(project(":core:sync"))
}
