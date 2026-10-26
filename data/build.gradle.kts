plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.javierreansyah.pinecone.data"
    compileSdk = 37
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

kotlin { compilerOptions { allWarningsAsErrors.set(true) } }

androidComponents.onVariants { variant ->
    variant.deviceTests.values.forEach { test ->
        test.sources.assets?.addStaticSourceDirectory(layout.projectDirectory.dir("schemas").asFile.absolutePath)
        test.sources.java?.addStaticSourceDirectory(
            rootProject.file("test-support/src/androidTest/java").absolutePath,
        )
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)
    api(project(":core:model"))
    implementation(project(":publication"))
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.core.okio)
    testImplementation(libs.okio)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.runner)
}
