import groovy.json.JsonSlurper

plugins { alias(libs.plugins.android.library) }

val pin = JsonSlurper().parse(file("source.json")) as Map<*, *>
group = "com.artifex.mupdf"
version = pin["version"].toString()
require(version == libs.versions.mupdf.get()) { "Update the MuPDF source pin and version catalog together." }

android {
    namespace = "com.artifex.mupdf.fitz"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    ndkVersion = pin["ndk"].toString()
    defaultConfig {
        minSdk = 34
        val targetAbi = providers.gradleProperty("abiFilter").orNull
        if (targetAbi != null) {
            ndk { abiFilters += targetAbi }
        } else {
            ndk { abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        }
        externalNativeBuild {
            ndkBuild {
                arguments += "-j2"
            }
        }
    }
    externalNativeBuild { ndkBuild { path = file("source/platform/java/Android.mk") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addStaticSourceDirectory(
            layout.projectDirectory.dir("source/platform/java/src").asFile.absolutePath
        )
    }
}
