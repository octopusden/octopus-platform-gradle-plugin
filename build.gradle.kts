import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.time.Duration

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin")
    id("com.jfrog.artifactory")
    id("dev.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.octopusden.octopus-quality")
    id("org.sonarqube")
}

description = "Octopus platform Gradle plugin (aggregator)"

octopusQuality {
    // Regression guard on what this repository publishes to Maven Central, provided by the shared
    // policy in octopus-base v2.7.0. This branch previously hand-rolled a task of exactly that
    // name; the plugin registers the same name, so the deletion and the version bump land in one
    // commit — keeping both fails configuration.
    //
    // Nothing here is oversized and nothing is dropped: the plugin and its marker are exactly what
    // Central is for. The guard exists so that publishing more stays a decision rather than a
    // default — `java-gradle-plugin` creates publications on its own, and a second plugin id
    // declared in `gradlePlugin {}` would silently add another marker coordinate — ~10 more files
    // per release, against an organisation-wide limit the organisation currently exceeds.
    //
    // The marker's groupId is NOT derivable from the repository name: across this organisation the
    // pattern is sometimes `octopus-<name>` and sometimes `octopus.<name>`. It is read from this
    // repository's own plugin id. Its artifact list is empty — the marker is a bare POM.
    publication {
        enforceCentralPublications.set(true)
        centralPublications.set(
            setOf(
                // The plugin itself.
                ":|pluginMaven|org.octopusden.octopus.platform:octopus-platform-gradle-plugin|" +
                    "[jar, jar:javadoc, jar:sources]",
                // The plugin-id marker `java-gradle-plugin` publishes under its OWN groupId —
                // note `octopus-platform`, not `octopus.platform` as the coordinate above uses.
                // It is a bare POM, hence the empty artifact list.
                ":|OctopusPlatformPluginPluginMarkerMaven|" +
                    "org.octopusden.octopus-platform:org.octopusden.octopus-platform.gradle.plugin|[]",
            ),
        )
    }
    // No jacoco/kover in this repo — keep coverage verification off.
    coverage {
        enabled.set(false)
    }
    // Baselines absorb current debt; regressions fail the build.
    kotlin {
        failOnViolation.set(true)
    }
}

// The convention plugin configures and aggregates SUBPROJECTS only (see OctopusQualityPlugin:
// when subprojects exist, the root project is treated as a pure aggregator). This repo, however,
// carries the plugin implementation source in the root module, so detekt & ktlint are applied and
// configured here directly and folded into the aggregate `qualityStatic` task.
// detekt 2.x splits its baselines per source set (detekt-baseline-main.xml / -test.xml) for the
// type-resolution-enabled detektMain/detektTest tasks; the umbrella `detekt` task uses the shared
// file named here. Same shape as the portal, which migrated first.
//
// None of those files exist, deliberately: this repository has zero detekt findings, so there is
// nothing to suppress. The path is declared so that adding a baseline later needs no config change
// — a missing baseline file is treated as empty.
//
// The jvm-target pin that used to live here is gone with detekt 1.23.x: it never addressed the real
// problem. 1.23.x embeds an older Kotlin compiler whose `isAtLeastJava9` cannot parse a runtime
// version string like "25.0.1", so `:detekt` threw IllegalArgumentException on any JDK 24+ agent
// regardless of which bytecode target it was told to emit.
detekt {
    baseline = file("detekt-baseline.xml")
}

ktlint {
    baseline.set(file("ktlint-baseline.xml"))
    ignoreFailures.set(false)
}

gradle.projectsEvaluated {
    tasks.named("qualityStatic") {
        dependsOn("detekt", "ktlintCheck")
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:${project.property("jackson-bom.version")}"))

    api(
        "org.octopusden.octopus-build-integration:org.octopusden.octopus-build-integration.gradle.plugin:${project.property(
            "octopus-build-integration.version",
        )}",
    )
    api(
        "org.octopusden.octopus-publishing:org.octopusden.octopus-publishing.gradle.plugin:${project.property(
            "octopus-publishing.version",
        )}",
    )
    api(
        "org.octopusden.octopus.license-management:org.octopusden.octopus.license-management.gradle.plugin:${project.property(
            "octopus-license-management.version",
        )}",
    )
    implementation("org.sonarqube:org.sonarqube.gradle.plugin:${project.property("sonarqube.version")}")

    testImplementation(platform("org.junit:junit-bom:${project.property("junit-jupiter.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:${project.property("assertj.version")}")
}

// The build/quality toolchain follows the JVM the workflow provisions (the Gradle daemon JVM),
// overridable via -Poctopus.build.jdk. Tracking the running JVM means compilation never needs a
// second, un-provisioned JDK. Bytecode is pinned to 17 regardless (below).
//
// This used to require the quality gate to provision JDK 21 specifically, because detekt 1.23.x
// could not run on JDK 24+. That is what made the two CI systems disagree: the GitHub quality
// workflow provisioned 21 and passed, while the TeamCity build ran on a JDK 25 agent and failed in
// `:detekt`. detekt 2.x carries a current Kotlin compiler and has no such ceiling.
val octopusBuildJdk =
    (findProperty("octopus.build.jdk") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: JavaVersion.current().majorVersion
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(octopusBuildJdk)
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("OctopusPlatformPlugin") {
            id = "org.octopusden.octopus-platform"
            displayName = project.name
            description = project.description
            implementationClass = "org.octopusden.octopus.platform.gradle.OctopusPlatformPlugin"
        }
    }
}

artifactory {
    publish {
        val baseUrl = System.getenv("ARTIFACTORY_URL") ?: (project.properties["artifactoryUrl"] as? String)
        if (baseUrl != null) {
            contextUrl = "$baseUrl/artifactory"
        }
        repository {
            // Dev/snapshot sink only — releases go to Sonatype Central (see `nexusPublishing` below)
            repoKey = "rnd-maven-dev-local"
            username = System.getenv("ARTIFACTORY_DEPLOYER_USERNAME")
            password = System.getenv("ARTIFACTORY_DEPLOYER_PASSWORD")
        }
        defaults {
            publications("ALL_PUBLICATIONS")
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("MAVEN_USERNAME"))
            password.set(System.getenv("MAVEN_PASSWORD"))
        }
    }
    transitionCheckOptions {
        maxRetries.set(60)
        delayBetween.set(Duration.ofSeconds(30))
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/octopusden/octopus-platform-gradle-plugin.git")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/octopusden/octopus-platform-gradle-plugin.git")
                    connection.set("scm:git://github.com/octopusden/octopus-platform-gradle-plugin.git")
                }
                developers {
                    developer {
                        id.set("octopus")
                        name.set("octopus")
                    }
                }
            }
        }
    }
}

signing {
    isRequired = System.getenv().containsKey("ORG_GRADLE_PROJECT_signingKey") &&
        System.getenv().containsKey("ORG_GRADLE_PROJECT_signingPassword")
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}
