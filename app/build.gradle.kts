plugins {
    alias(libs.plugins.gameport.android.application)
    alias(libs.plugins.gameport.android.compose)
    alias(libs.plugins.gameport.android.hilt)
}

android {
    namespace = "app.gameport"

    defaultConfig {
        applicationId = "app.gameport"
        versionCode = 702
        versionName = "0.7.2"
    }

    // One debug key for everyone: a build from a developer's machine and one from the CI update each other.
    // It was made for this project only and is public on purpose (password "android"): it signs test builds, never a release.
    buildTypes {
        // A test build lives next to the released GamePort: its own package name, so its own games, key and provider.
        getByName("debug") {
            applicationIdSuffix = ".dev"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    packaging {
        resources {
            // Several libraries ship the same license notices; the first copy is enough.
            pickFirsts += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:device"))
    implementation(project(":core:install"))
    implementation(project(":core:model"))
    implementation(project(":core:patch"))
    implementation(project(":core:settings"))
    implementation(project(":core:steam"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:game"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:sync"))
    implementation(project(":core:sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
