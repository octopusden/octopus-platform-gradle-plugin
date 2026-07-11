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
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.octopusden.octopus-quality")
}

description = "Octopus platform Gradle plugin (aggregator)"

octopusQuality {
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
detekt {
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
    ignoreFailures = false
}

// detekt 1.23.x validates --jvm-target against a max of 22; bytecode target is 17 anyway,
// so pin detekt's jvm-target to 17.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
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
// overridable via -Poctopus.build.jdk. detekt 1.23.x bundles Kotlin 2.0.21, whose embedded compiler
// cannot run on JDK 24+ (fails parsing the runtime version string), so the quality gate provisions
// JDK 21; release/security keep running on their own JDK. Tracking the running JVM here means
// compilation never needs a second, un-provisioned JDK. Bytecode is pinned to 17 regardless (below).
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
