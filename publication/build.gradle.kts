plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.javierreansyah.pinecone.publication"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

@Suppress("UnstableApiUsage")
androidComponents {
    beforeVariants { variant ->
        (variant as com.android.build.api.variant.HasHostTestsBuilder)
            .hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable =
            true
    }
    onVariants { variant ->
        variant.deviceTests.values.forEach { test ->
            test.sources.java?.addStaticSourceDirectory(
                rootProject.file("test-support/src/androidTest/java").absolutePath,
            )
        }
    }
}

kotlin { compilerOptions { allWarningsAsErrors.set(true) } }

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)
    api(project(":core:model"))
    implementation(libs.androidx.documentfile)
    api(libs.readium.shared)
    implementation(libs.mupdf)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":data"))
    androidTestImplementation(libs.readium.streamer)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.timber)
}
