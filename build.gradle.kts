plugins {
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dependency.analysis) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

if (providers.gradleProperty("mupdfFromSource").map(String::toBoolean).getOrElse(false)) {
    subprojects {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("com.artifex.mupdf:fitz")).using(project(":mupdf"))
            }
        }
    }
}

// Upstream currently tests through AGP 9.3.1; analysis is opt-in on this AGP 9.4 project.
if (providers.gradleProperty("analyzeDependencies").map(String::toBoolean).getOrElse(false)) {
    pluginManager.apply("com.autonomousapps.dependency-analysis")
}
