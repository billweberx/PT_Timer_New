// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    repositories {
        // Repositories are managed in settings.gradle.kts
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.0") // Match AGP version from libs.versions.toml
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21") // Match Kotlin version
    }
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
