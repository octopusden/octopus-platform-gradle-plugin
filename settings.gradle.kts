pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version settings.providers.gradleProperty("kotlin.version")
        id("io.github.gradle-nexus.publish-plugin") version settings.providers.gradleProperty("nexus-plugin.version")
        id("com.jfrog.artifactory") version settings.providers.gradleProperty("com-jfrog-artifactory.version")
        id("io.gitlab.arturbosch.detekt") version settings.providers.gradleProperty("detekt.version")
        id("org.jlleitschuh.gradle.ktlint") version settings.providers.gradleProperty("ktlint-gradle.version")
        id("org.octopusden.octopus-quality") version settings.providers.gradleProperty("octopus-quality.version")
    }
}

rootProject.name = "octopus-platform-gradle-plugin"

include("ft")
