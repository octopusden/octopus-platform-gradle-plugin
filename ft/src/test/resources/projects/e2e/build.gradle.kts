plugins {
    `java-library`
    `maven-publish`
    id("org.octopusden.octopus-platform")
}

group = "org.octopusden.platform.ft.e2e"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.13")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
