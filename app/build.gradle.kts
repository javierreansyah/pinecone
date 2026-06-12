plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun generateVersionCode(): Int {
    val offset = 1_704_067_200L
    return ((System.currentTimeMillis() / 1000L) - offset).toInt()
}

val appVersionName = "1.0"

android {
    namespace = "com.example.readerapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.readerapp"
        minSdk = 34
        targetSdk = 37
        versionCode = generateVersionCode()
        versionName = appVersionName
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appName"] = "Pinecone Dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "Pinecone"

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("debug")
        }

        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appName"] = "Pinecone Dev"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-rules-staging.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

androidComponents {
    onVariants { variant ->
        val outputName = when (variant.name) {
            "release" -> "pinecone-v${appVersionName}.apk"
            "debug" -> "pinecone-${appVersionName}-debug.apk"
            "staging" -> "pinecone-${appVersionName}-staging.apk"
            else -> "pinecone-${appVersionName}-${variant.name}.apk"
        }
        variant.outputs.forEach { output ->
            output.outputFileName.set(outputName)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.reorderable)
    implementation(libs.materialKolor)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    //noinspection UseTomlInstead
    implementation("com.composables:icons-material-symbols-outlined-cmp:2.2.1")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    coreLibraryDesugaring(libs.desugar.jdk)
    debugImplementation(libs.androidx.compose.ui.tooling)
    //noinspection UseTomlInstead
    implementation("androidx.compose.ui:ui-text-google-fonts:1.11.2")
    implementation(libs.kotlinx.serialization.json)
}