plugins {
    alias(libs.plugins.gameport.android.library)
    alias(libs.plugins.gameport.android.hilt)
}

android {
    namespace = "app.gameport.core.patch"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.arsclib)
    implementation(libs.commons.compress)
    implementation(libs.apksig)
    implementation(libs.bouncycastle.pkix)

    testImplementation(libs.junit)
}
