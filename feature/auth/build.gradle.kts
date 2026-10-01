plugins {
    alias(libs.plugins.gameport.android.feature)
}

android {
    namespace = "app.gameport.feature.auth"
}

dependencies {
    implementation(libs.zxing.core)
}
