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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
