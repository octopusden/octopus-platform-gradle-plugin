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

// Regression guard on what this repository publishes to Maven Central. Nothing here is oversized
// and nothing is being dropped — the plugin and its marker are exactly what Central is for. The
// guard exists so that becomes a decision rather than a default: `java-gradle-plugin` creates
// publications on its own, and a second plugin id declared in `gradlePlugin {}` would silently add
// another marker coordinate — ~10 more files per release against an organisation-wide limit the
// organisation currently exceeds.
//
// The identity is a COMPOSITE key — project path, publication name, coordinate, and the sorted
// artifact signatures (extension and classifier). Path alone is too weak: both publications live
// in the same project, so a path-based set could not tell them apart, and it would not notice a
// classifier being added to either.
//
// allprojects, not subprojects: the root is a publishable project like any other.
val centralPublishedPublications = setOf(
    // The plugin itself. Its artifact list has no `module` entry because Gradle module metadata is
    // generated at publish time rather than being an attached artifact — the published coordinate
    // does carry a .module file.
    ":|pluginMaven|org.octopusden.octopus.platform:octopus-platform-gradle-plugin|" +
        "[jar, jar:javadoc, jar:sources]",
    // The plugin-id marker that `java-gradle-plugin` publishes under its OWN groupId (note the
    // dash, unlike the dotted group above). It carries no artifacts of its own — only a pom and
    // module — which is why the signature list is empty. Declaring a second plugin id in
    // `gradlePlugin {}` would add another marker here and fail this check.
    ":|OctopusPlatformPluginPluginMarkerMaven|" +
        "org.octopusden.octopus-platform:org.octopusden.octopus-platform.gradle.plugin|[]",
)

fun centralPublicationPolicyProblems(): List<String> {
    // Reading `publishing` throws on a project without maven-publish, so check the plugin first.
    val actual = allprojects
        .filter { it.plugins.hasPlugin("maven-publish") }
        .flatMap { proj ->
            proj.extensions
                .getByType(PublishingExtension::class.java)
                .publications
                .withType(MavenPublication::class.java)
                .map { pub ->
                    val signatures = pub.artifacts
                        .map { a -> listOfNotNull(a.extension, a.classifier).joinToString(":") }
                        .sorted()
                    "${proj.path}|${pub.name}|${pub.groupId}:${pub.artifactId}|$signatures"
                }
        }.toSet()
    return if (actual != centralPublishedPublications) {
        listOf(
            "Maven Central publication set drifted.\n" +
                "  allowlisted: ${centralPublishedPublications.sorted()}\n" +
                "  publishing:  ${actual.sorted()}\n" +
                "Update centralPublishedPublications only if the change is intentional.",
        )
    } else {
        emptyList()
    }
}

// A policy violation must fail its own gate, not every Gradle invocation: throwing at
// configuration time would break build, test, dependencies and IDE sync as well.
val verifyCentralPublicationPolicy =
    tasks.register("verifyCentralPublicationPolicy") {
        group = "verification"
        description = "Fails if the set of publications reaching Maven Central drifts from the allowlist."
        doLast {
            val problems = centralPublicationPolicyProblems()
            if (problems.isNotEmpty()) {
                throw GradleException(problems.joinToString("\n\n"))
            }
        }
    }

// Hook the task TYPE, so a concrete publish task cannot bypass the guard; the aggregates are
// matched by name as well because `publish` is per-project and `publishToSonatype` only exists
// with -Pnexus, so neither can be forced into existence.
gradle.projectsEvaluated {
    allprojects {
        tasks.withType(AbstractPublishToMaven::class.java).configureEach {
            dependsOn(verifyCentralPublicationPolicy)
        }
        tasks
            .matching { it.name in setOf("publishToSonatype", "publish", "publishToMavenLocal") }
            .configureEach { dependsOn(verifyCentralPublicationPolicy) }
    }
}
