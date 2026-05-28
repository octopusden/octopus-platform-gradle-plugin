pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.octopusden.octopus-platform") {
                val v = (settings.providers.gradleProperty("octopus-platform.version").orNull
                    ?: System.getenv("OCTOPUS_PLATFORM_VERSION")
                    ?: "1.0-SNAPSHOT")
                useVersion(v)
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "subproject-only-imperative"
include("child")
