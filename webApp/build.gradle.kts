import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
    source.setFrom(files("src"))
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("cloudbase-predictor")
        browser {
            commonWebpackConfig {
                outputFileName = "cloudbase-predictor.js"
                sourceMaps = false
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.compose.ui.tooling.preview)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(npm("maplibre-gl", libs.versions.maplibreGl.get()))
        }

        wasmJsTest.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
