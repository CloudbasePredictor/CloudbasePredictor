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

// Generate WebBuildConfig with the app version so the web About screen shows the
// build version instead of a duplicated hardcoded constant. Single source of
// truth is the `cloudbaseVersionName` Gradle property (also used by :app).
val generateWebBuildConfig by tasks.registering {
    val version = providers.gradleProperty("cloudbaseVersionName").getOrElse("dev")
    val outputDir = layout.buildDirectory.dir("generated/webBuildConfig/commonMain")
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val target = outputDir.get().asFile
            .resolve("com/cloudbasepredictor/web/WebBuildConfig.kt")
        target.parentFile.mkdirs()
        target.writeText(
            "package com.cloudbasepredictor.web\n\n" +
                "// Generated from the cloudbaseVersionName Gradle property. Do not edit.\n" +
                "internal object WebBuildConfig {\n" +
                "    const val VERSION: String = \"$version\"\n" +
                "}\n",
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateWebBuildConfig)
}
