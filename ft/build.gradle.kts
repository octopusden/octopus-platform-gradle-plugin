plugins {
    kotlin("jvm")
}

version = "1.0-SNAPSHOT"
group = "org.octopusden.octopus.platform.ft"

description = "octopus-platform-gradle-plugin functional tests"

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.platformlib:platformlib-process-local:${project.property("platformlib-process.version")}")
    implementation("org.slf4j:slf4j-api:${project.property("slf4j.version")}")

    testImplementation(platform("org.junit:junit-bom:${project.property("junit-jupiter.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:${project.property("assertj.version")}")
    testRuntimeOnly("ch.qos.logback:logback-classic:${project.property("logback.version")}")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    dependsOn(rootProject.tasks.named("publishToMavenLocal"))
    useJUnitPlatform()
    val pluginVersion = providers.gradleProperty("octopus-platform.version")
        .orElse(providers.environmentVariable("OCTOPUS_PLATFORM_VERSION"))
        .getOrElse(rootProject.version.toString())
    environment("OCTOPUS_PLATFORM_VERSION", pluginVersion)
    systemProperty("octopus-platform.version", pluginVersion)
    testLogging.showStandardStreams = true
}
