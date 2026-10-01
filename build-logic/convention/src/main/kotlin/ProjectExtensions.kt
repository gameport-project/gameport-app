import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 29
internal const val TARGET_SDK = 36

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun configureAndroid(extension: CommonExtension) {
    extension.compileSdk = COMPILE_SDK
    extension.defaultConfig.minSdk = MIN_SDK
    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17
}
