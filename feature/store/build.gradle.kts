plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.store"
}

dependencies {
    implementation(project(":core:device"))
    implementation(project(":feature:compat"))
    implementation(project(":core:settings"))
    implementation(project(":core:sync"))
}
