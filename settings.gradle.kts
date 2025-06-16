pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    versionCatalogs {
        create("libs") {
            version("error-prone", "2.10.0")
            library("error-prone-annotations", "com.google.errorprone", "error_prone_annotations").versionRef("error-prone")
        }
    }
}

rootProject.name = "A Blind Examiner"
include(":app")
 