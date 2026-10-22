plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

abstract class BuildTimestampVersionCode : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int {
        val offset = 1_704_067_200L // 2024-01-01 UTC; keep existing installed versions compatible.
        return Math.toIntExact(System.currentTimeMillis() / 1000L - offset)
    }
}

val buildVersionCode = providers.of(BuildTimestampVersionCode::class) {}
val appVersionName: String = "1.0"

abstract class PublicationLicenseAssetsTask : DefaultTask() {
    @get:InputFiles
    abstract val licenseFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        licenseFiles.files.forEach { it.copyTo(directory.resolve(it.name), overwrite = true) }
    }
}

val publicationLicenseAssets: TaskProvider<PublicationLicenseAssetsTask> =
    tasks.register<PublicationLicenseAssetsTask>("publicationLicenseAssets") {
        description = ""
        licenseFiles.from(rootProject.file("COPYING"), rootProject.file("LICENSE.md"))
        outputDirectory.set(layout.buildDirectory.dir("generated/publicationLicenseAssets"))
    }

android {
    namespace = "com.javierreansyah.pinecone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.javierreansyah.pinecone"
        minSdk = 34
        targetSdk = 37
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

            signingConfig = signingConfigs.getByName("debug")
        }

        create("staging") {
            initWith(getByName("release"))
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
        viewBinding = true
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
        variant.sources.assets?.addGeneratedSourceDirectory(
            publicationLicenseAssets,
            PublicationLicenseAssetsTask::outputDirectory
        )
        val outputName = when (variant.name) {
            "release" -> "pinecone-v$appVersionName.apk"
            "debug" -> "pinecone-$appVersionName-debug.apk"
            "staging" -> "pinecone-$appVersionName-staging.apk"
            else -> "pinecone-$appVersionName-${variant.name}.apk"
        }
        variant.outputs.forEach { output ->
            // Keep the clock lazy so cached task graphs cannot restore an older timestamp.
            output.versionCode.set(buildVersionCode)
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
    implementation(libs.composables.icons.material.symbols.outlined)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
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
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    coreLibraryDesugaring(libs.desugar.jdk)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
}
