plugins {
    `java-library`
    `maven-publish`
}

apply(plugin = "org.octopusden.octopus-platform")

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
