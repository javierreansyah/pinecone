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

abstract class BuildTimestampVersionCode : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int {
        val offset = 1_704_067_200L // 2024-01-01 UTC; keep existing installed versions compatible.
        return Math.toIntExact(System.currentTimeMillis() / 1000L - offset)
    }
}

val buildVersionCode: Provider<Int> = providers.of(BuildTimestampVersionCode::class) {}
val appVersionName: String = "1.0"

abstract class PublicationLicenseAssetsTask : DefaultTask() {
    @get:InputFiles
    abstract val licenseFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        val files = licenseFiles.files
        val names = files.mapTo(mutableSetOf()) { it.name }
        directory.listFiles()?.filter { it.isFile && it.name !in names }?.forEach {
            check(it.delete()) { "Cannot remove obsolete generated notice: $it" }
        }
        files.forEach { it.copyTo(directory.resolve(it.name), overwrite = true) }
    }
}

val publicationLicenseAssets: TaskProvider<PublicationLicenseAssetsTask> =
    tasks.register<PublicationLicenseAssetsTask>("publicationLicenseAssets") {
        description = ""
        licenseFiles.from(rootProject.file("COPYING"))
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
    ksp(libs.room.compiler)
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

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
}
