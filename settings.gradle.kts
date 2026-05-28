pluginManagement {
    plugins {
        kotlin("jvm") version settings.providers.gradleProperty("kotlin.version")
        id("io.github.gradle-nexus.publish-plugin") version settings.providers.gradleProperty("nexus-plugin.version")
        id("com.jfrog.artifactory") version settings.providers.gradleProperty("com-jfrog-artifactory.version")
    }
}

rootProject.name = "octopus-platform-gradle-plugin"

include("ft")
