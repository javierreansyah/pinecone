// Development-only repository. The F-Droid recipe deletes this file before scanning.
dependencyResolutionManagement {
    repositories {
        maven("https://maven.ghostscript.com") {
            content { includeGroup("com.artifex.mupdf") }
        }
    }
}
