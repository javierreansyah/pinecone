plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
