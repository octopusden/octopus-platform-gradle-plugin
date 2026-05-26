plugins {
    `java-library`
    `maven-publish`
    id("org.octopusden.octopus-platform")
}

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
