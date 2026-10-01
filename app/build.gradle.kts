plugins {
    alias(libs.plugins.gameport.android.application)
    alias(libs.plugins.gameport.android.compose)
    alias(libs.plugins.gameport.android.hilt)
}

android {
    namespace = "app.gameport"

    defaultConfig {
        applicationId = "app.gameport"
        versionCode = 500
        versionName = "0.5.0"
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
