plugins {
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

lint {
    lintConfig = rootProject.file("lint.xml")
    abortOnError = true
    warningsAsErrors = true
    checkAllWarnings = true
    checkTestSources = true
}

kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)
    testImplementation(libs.junit)
}
