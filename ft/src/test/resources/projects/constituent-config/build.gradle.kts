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
    // `isSkipProject` is a read/write property on SonarExtension and
    // serves as the observable signal that this `configure<SonarExtension>`
    // block actually reached the plugin. Default is `false`.
    isSkipProject = true
    properties {
        property("sonar.projectKey", "ft-custom-project-key")
        property("sonar.host.url", "http://localhost:9000")
    }
}

tasks.register("verifyConfig") {
    doLast {
        val ext = project.extensions.getByType(SonarExtension::class.java)
        println("sonar.skipProject=${ext.isSkipProject}")
    }
}
