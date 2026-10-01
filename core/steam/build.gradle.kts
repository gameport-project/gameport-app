plugins {
    alias(libs.plugins.gameport.android.library)
    alias(libs.plugins.gameport.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.gameport.core.steam"
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javasteam)
    // JavaSteam only brings OkHttp at run time; the cloud transfers use it directly.
    implementation(libs.okhttp)
    // The cloud file states come from JavaSteam's generated protobuf types.
    implementation(libs.protobuf.java)
    implementation(libs.javasteam.depotdownloader)
    // JavaSteam looks this crypto provider up by name on Android.
    implementation(libs.spongycastle)
    // Depot chunks are zstd- or LZMA-compressed; JavaSteam leaves both decoders to the app.
    implementation(libs.zstd.jni) { artifact { type = "aar" } }
    implementation(libs.xz)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
