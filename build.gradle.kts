// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {

// build.gradle.kts (module)
    plugins {
        // Android App Plugin - Version is declared in libs.versions.toml
        alias(libs.plugins.android.application) apply false

        // Kotlin Android Plugin - Version is declared in libs.versions.toml
        alias(libs.plugins.kotlin.android) apply false

        // Kotlin Compose Plugin - Version is declared in libs.versions.toml
        alias(libs.plugins.compose.compiler) apply false
    }

}
