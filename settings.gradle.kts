pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kotlin/Wasm toolchain distributions, declared centrally so the
        // Kotlin plugin does not need project-level repositories (their
        // registration is suppressed in the root build.gradle.kts).
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Node.js Distributions"
                    url = uri("https://nodejs.org/dist")
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Yarn Distributions"
                    url = uri("https://github.com/yarnpkg/yarn/releases/download")
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.yarnpkg", "yarn") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Binaryen Distributions"
                    url = uri("https://github.com/WebAssembly/binaryen/releases/download")
                    patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "Cloudbase predictor"
include(":app")
include(":engine")
include(":shared")
include(":webApp")
