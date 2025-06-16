// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.google.errorprone:error_prone_annotations:2.10.0")
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("com.google.errorprone:error_prone_annotations:2.10.0")
        }
    }
}