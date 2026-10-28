plugins {
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

if (providers.gradleProperty("analyzeDependencies").map(String::toBoolean).getOrElse(false)) {
    pluginManager.apply("com.autonomousapps.dependency-analysis")
}

val releaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .associateWith { providers.gradleProperty("pinecone.signing.$it").orNull }
require(releaseSigning.values.all { it == null } || releaseSigning.values.all { !it.isNullOrBlank() }) {
    "Set all four pinecone.signing properties, or omit all four for an unsigned release."
}
// Android Studio's signing wizard supplies AGP credentials separately from our private properties.
val releaseSigningConfigured = releaseSigning["storeFile"] != null ||
        providers.gradleProperty("android.injected.signing.store.file").isPresent

abstract class PublicationLicenseAssetsTask : DefaultTask() {
    @get:InputFiles
    abstract val licenseFiles: ConfigurableFileCollection

    @get:InputFiles
    abstract val nativeLicenseFiles: ConfigurableFileCollection

    @get:Internal
    abstract val nativeLicenseRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        val files = licenseFiles.files.associateBy { it.name } + nativeLicenseFiles.files.associateBy {
            "mupdf/" + it.relativeTo(nativeLicenseRoot.get().asFile).invariantSeparatorsPath
        }
        directory.walkTopDown().filter { it.isFile && it.relativeTo(directory).invariantSeparatorsPath !in files }.forEach {
            check(it.delete()) { "Cannot remove obsolete generated notice: $it" }
        }
        files.forEach { (name, source) ->
            val target = directory.resolve(name)
            target.parentFile.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }
}

val publicationLicenseAssets: TaskProvider<PublicationLicenseAssetsTask> =
    tasks.register<PublicationLicenseAssetsTask>("publicationLicenseAssets") {
        description = ""
        licenseFiles.from(rootProject.file("COPYING"))
        nativeLicenseRoot.set(rootProject.layout.projectDirectory.dir("native/mupdf/source"))
        if (providers.gradleProperty("mupdfFromSource").map(String::toBoolean).getOrElse(false)) {
            nativeLicenseFiles.from(rootProject.fileTree("native/mupdf/source") {
                include("COPYING", "thirdparty/**/COPYING*", "thirdparty/**/LICENSE*", "thirdparty/**/NOTICE*", "thirdparty/**/OFL*", "thirdparty/**/FTL.TXT", "resources/**/OFL*", "resources/**/LICENSE*", "resources/**/COPYING*")
                exclude("**/.git/**", "**/build/**")
            })
        }
        outputDirectory.set(layout.buildDirectory.dir("generated/publicationLicenseAssets"))
    }

android {
    namespace = "com.javierreansyah.pinecone"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.javierreansyah.pinecone"
        minSdk = 34
        targetSdk = 37
        versionCode = 100000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val targetAbi = providers.gradleProperty("abiFilter").orNull
    splits {
        abi {
            isEnable = true
            reset()
            if (targetAbi != null) {
                include(targetAbi)
            } else {
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
            isUniversalApk = false
        }
    }

    signingConfigs {
        if (releaseSigning.values.all { it != null }) {
            create("production") {
                storeFile = file(requireNotNull(releaseSigning["storeFile"]))
                storePassword = releaseSigning["storePassword"]
                keyAlias = releaseSigning["keyAlias"]
                keyPassword = releaseSigning["keyPassword"]
            }
        }
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
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro",
            )

            signingConfig = signingConfigs.findByName("production")
        }

        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appName"] = "Pinecone Dev"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-rules-staging.pro",
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
    }

    lint {
        lintConfig = file("../lint.xml")
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkTestSources = true
        checkDependencies = true
        // Text proofreading is outside this project's code-validation scope.
        disable += setOf(
            "TypographyDashes", "TypographyEllipsis", "TypographyFractions",
            "TypographyOther", "TypographyQuotes",
        )
    }
}

@Suppress("UnstableApiUsage")
androidComponents {
    beforeVariants { variant ->
        // Complete validation runs the shared JVM tests against every build type.
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
        variant.sources.assets?.addGeneratedSourceDirectory(
            publicationLicenseAssets,
            PublicationLicenseAssetsTask::outputDirectory
        )
        val appVersionName = android.defaultConfig.versionName
        val baseVersionCode = android.defaultConfig.versionCode ?: 100000
        val abiCodeOffsets = mapOf(
            "armeabi-v7a" to 1,
            "arm64-v8a" to 2,
            "x86" to 3,
            "x86_64" to 4,
        )
        variant.outputs.forEach { output ->
            val abi = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val abiOffset = abi?.let { abiCodeOffsets[it] } ?: 0
            output.versionCode.set(baseVersionCode + abiOffset)
            val abiSuffix = if (abi != null) "-$abi" else ""
            val outputName = when (variant.name) {
                "release" -> "pinecone-v$appVersionName$abiSuffix${if (releaseSigningConfigured) "" else "-unsigned"}.apk"
                "debug" -> "pinecone-$appVersionName$abiSuffix-debug.apk"
                "staging" -> "pinecone-$appVersionName$abiSuffix-staging.apk"
                else -> "pinecone-$appVersionName$abiSuffix-${variant.name}.apk"
            }
            output.outputFileName.set(outputName)
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":publication"))
    implementation(project(":data"))
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.reorderable)
    implementation(libs.materialKolor)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.compose.material3)
    implementation(libs.composables.icons.material.symbols.outlined)
    implementation(libs.composables.icons.material.symbols.outlined.filled)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.audio)
    implementation(libs.readium.exoplayer)
    implementation(libs.mupdf)
    implementation(libs.androidx.media3.session)
    implementation(libs.timber)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.room.runtime)
    implementation(libs.work.runtime)
    coreLibraryDesugaring(libs.desugar.jdk)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.compose.ui.geometry)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.media3.common)
    implementation(libs.dagger)
    implementation(libs.hilt.core)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.core.okio)
    testImplementation(libs.okio)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.work.testing)
    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// Include module tests in Quick and Complete; staging consumes release library variants.
tasks.matching {
    it.name in setOf(
        "testDebugUnitTest",
        "testReleaseUnitTest",
        "testStagingUnitTest"
    )
}.configureEach {
    val libraryBuildType = if (name == "testDebugUnitTest") "Debug" else "Release"
    dependsOn(
        ":core:model:test",
        ":publication:test${libraryBuildType}UnitTest",
        ":data:test${libraryBuildType}UnitTest"
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}

aboutLibraries {
    if (providers.gradleProperty("mupdfFromSource").map(String::toBoolean).getOrElse(false)) {
        collect { configPath.set(layout.projectDirectory.dir("config/aboutlibraries-source")) }
    }
}
