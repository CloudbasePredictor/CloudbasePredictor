// Top-level build file where you can add configuration options common to all sub-projects/modules.
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The generated npm root package.json inherits the Gradle root project name
// ("Cloudbase predictor"), which is not a legal npm/yarn package name. The
// file is a private yarn-workspaces aggregator, so rewrite the name to a
// sanitized slug before the package manager consumes it.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.RootPackageJsonTask>().configureEach {
    val illegalName = "\"name\": \"${project.name}\""
    val sanitizedName = "\"name\": \"${project.name.lowercase().replace(Regex("[^a-z0-9-]+"), "-")}\""
    doLast {
        val packageJsonFile = rootPackageJsonFile.get().asFile
        val original = packageJsonFile.readText()
        val sanitized = original.replaceFirst(illegalName, sanitizedName)
        if (sanitized != original) {
            packageJsonFile.writeText(sanitized)
        }
    }
}

// The Kotlin/Wasm toolchain (Node.js, Yarn, Binaryen) normally registers ivy
// download repositories on the root and target projects, which
// settings.gradle.kts forbids via FAIL_ON_PROJECT_REPOS. Clearing
// downloadBaseUrl suppresses those registrations; the same distribution
// repositories are declared centrally in settings.gradle.kts, so the
// downloads keep working.
allprojects {
    fun org.gradle.api.provider.Property<String>.clear() {
        convention(null as String?)
        set(null as String?)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.clear()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>().downloadBaseUrl.clear()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().downloadBaseUrl.clear()
    }
}
