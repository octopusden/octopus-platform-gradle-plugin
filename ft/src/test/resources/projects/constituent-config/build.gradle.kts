import org.sonarqube.gradle.SonarExtension

plugins {
    `java-library`
    `maven-publish`
    id("org.octopusden.octopus-platform")
}

group = "org.octopusden.platform.ft.cfg"
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

configure<SonarExtension> {
    properties {
        property("sonar.projectKey", "ft-custom-project-key")
        property("sonar.host.url", "http://localhost:9000")
    }
}

tasks.register("verifyConfig") {
    doLast {
        println("CONSTITUENT_EXTENSION_CONFIGURED=true")
    }
}
