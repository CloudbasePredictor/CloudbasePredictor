import com.android.build.gradle.internal.tasks.FinalizeBundleTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val requestedTasks = gradle.startParameter.taskNames
val releaseArchiveName = "CloudbasePredictor"
val buildAbiSplitApks = requestedTasks.none { it.contains("bundle", ignoreCase = true) }
val buildUniversalApk = buildAbiSplitApks && !providers.gradleProperty("ABI_FILTERS").isPresent
val abiVersionCodeOffsets = linkedMapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4,
)
val requestedAbiFilters = providers.gradleProperty("ABI_FILTERS")
    .orNull
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    .orEmpty()
val activeAbiVersionCodeOffsets = if (requestedAbiFilters.isEmpty()) {
    abiVersionCodeOffsets
} else {
    linkedMapOf<String, Int>().apply {
        requestedAbiFilters.forEach { abi ->
            put(
                abi,
                requireNotNull(abiVersionCodeOffsets[abi]) {
                    "Unsupported ABI_FILTERS value: $abi"
                }
            )
        }
    }
}
val universalVersionCodeOffset = 0
fun releaseArtifactFileName(version: String, suffix: String = "", extension: String) =
    "${releaseArchiveName}_v$version$suffix.$extension"

android {
    namespace = "com.cloudbasepredictor"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.cloudbasepredictor"
        minSdk = 25
        targetSdk = 36
        versionCode = 8
        versionName = "1.3.0"

        testInstrumentationRunner = "com.cloudbasepredictor.HiltTestRunner"

        when {
            requestedTasks.any { it.endsWith("connectedE2eTest") } ->
                testInstrumentationRunnerArguments["class"] =
                    "com.cloudbasepredictor.e2e.ForecastModelE2eTest"

            requestedTasks.any { it.endsWith("connectedScreenshotTest") } ->
                testInstrumentationRunnerArguments["class"] =
                    "com.cloudbasepredictor.screenshot.ScreenshotCaptureTest"

            else ->
                testInstrumentationRunnerArguments["notClass"] = listOf(
                    "com.cloudbasepredictor.e2e.ForecastModelE2eTest",
                    "com.cloudbasepredictor.screenshot.ScreenshotCaptureTest",
                ).joinToString(",")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    splits {
        abi {
            isEnable = buildAbiSplitApks
            reset()
            include(*activeAbiVersionCodeOffsets.keys.toTypedArray())
            isUniversalApk = buildUniversalApk
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val version = requireNotNull(output.versionName.orNull) {
                "versionName must be set to name APK outputs"
            }
            val baseVersionCode = requireNotNull(output.versionCode.orNull) {
                "versionCode must be set to assign APK output version codes"
            }
            val buildTypeName = requireNotNull(variant.buildType) {
                "buildType must be set to name APK outputs"
            }
            val buildTypeSuffix = if (buildTypeName == "release") "" else "_$buildTypeName"
            val abi = output.filters
                .find {
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }
                ?.identifier
            val outputSuffix = abi?.let { "_$it" } ?: "_universal"

            val versionCodeOffset = if (abi != null) {
                requireNotNull(abiVersionCodeOffsets[abi]) {
                    "Unsupported ABI split output: $abi"
                }
            } else {
                universalVersionCodeOffset
            }
            output.versionCode.set(baseVersionCode * 10 + versionCodeOffset)

            (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                releaseArtifactFileName(
                    version = version,
                    suffix = "$outputSuffix$buildTypeSuffix",
                    extension = "apk"
                )
            )
        }
    }
}

val releaseVersionName = requireNotNull(android.defaultConfig.versionName) {
    "versionName must be set to name release bundle outputs"
}
val renamedReleaseBundleFileName = releaseArtifactFileName(
    version = releaseVersionName,
    extension = "aab"
)

afterEvaluate {
    tasks.named<FinalizeBundleTask>("signReleaseBundle") {
        finalBundleFile.set(
            layout.buildDirectory.file("outputs/bundle/release/$renamedReleaseBundleFileName")
        )
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.retrofit.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material)
    implementation(libs.maplibre.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.net.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.timber)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("connectedInstrumentationTest") {
    description = "Run instrumentation tests (excluding E2E and screenshot)"
    group = "verification"
    dependsOn("connectedDebugAndroidTest")
}

tasks.register("connectedE2eTest") {
    description = "Run E2E tests (requires emulator/device and network)"
    group = "verification"
    dependsOn("connectedDebugAndroidTest")
}

tasks.register("connectedScreenshotTest") {
    description = "Run screenshot capture tests (requires emulator/device)"
    group = "verification"
    dependsOn("connectedDebugAndroidTest")
}
