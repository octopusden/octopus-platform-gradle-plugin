package org.octopusden.octopus.platform.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * End-to-end execution against a single-module fixture without any
 * external infrastructure:
 *  - `exportDependencies` (from `octopus-build-integration`, scan disabled by default)
 *    runs and produces its output file
 *  - `publishToMavenLocal` (from `maven-publish` wired by `octopus-publishing`)
 *    publishes the artifact to the local Maven cache
 *  - `processLicensedDependencies` (from `octopus-license-management`) runs and
 *    produces its `build/dependencies.json` artifact
 *  - `sonar` task graph resolves successfully under `--dry-run` (no Sonar server hit)
 */
class EndToEndFT {

    @Test
    @DisplayName("e2e: exportDependencies + publishToMavenLocal run successfully")
    fun testExportAndPublishLocal() {
        val result = runGradle {
            testProjectName = "e2e"
            tasks = listOf("clean", "exportDependencies", "publishToMavenLocal")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        // exportDependencies emits its output file under build/
        val depsFile = result.projectPath.resolve(BUILD_INTEGRATION_DEPS_OUTPUT)
        assertThat(depsFile)
            .withFailMessage("Expected exportDependencies to produce %s", depsFile)
            .exists()

        // publishToMavenLocal generated the POM
        val pomPath = result.projectPath.resolve("build/publications/mavenJava/pom-default.xml")
        assertThat(pomPath).exists()

        // And published the jar
        val publishedJar = Files.walk(result.projectPath.resolve("build/libs")).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".jar") }.findFirst().orElse(null)
        }
        assertThat(publishedJar).isNotNull()
    }

    @Test
    @DisplayName("e2e: processLicensedDependencies produces build/dependencies.json")
    fun testProcessLicensedDependencies() {
        val result = runGradle {
            testProjectName = "e2e"
            tasks = listOf("clean", "processLicensedDependencies")
            additionalProperties = mapOf(
                // processLicensedDependencies.onlyIf gate (see LicenseGradlePlugin)
                "license.skip" to "false",
                "supported-groups" to "org.octopusden.octopus",
            )
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val depsFile = result.projectPath.resolve(LICENSE_DEPS_OUTPUT)
        assertThat(depsFile)
            .withFailMessage("Expected processLicensedDependencies to produce %s", depsFile)
            .exists()

        val depsJson = depsFile.toFile().readText()
        assertThat(depsJson)
            .withFailMessage("Expected %s to reference slf4j-api, but was:%n%s", depsFile, depsJson)
            .contains("slf4j")
    }

    @Test
    @DisplayName("e2e: sonar task graph resolves under --dry-run (no Sonar server required)")
    fun testSonarTaskGraphResolves() {
        val result = runGradle {
            testProjectName = "e2e"
            tasks = listOf("sonar", "--dry-run")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains(":sonar")
    }

    companion object {
        // Contract of `octopus-build-integration`'s `exportDependencies` task — owned by that plugin
        private const val BUILD_INTEGRATION_DEPS_OUTPUT = "build/components-dependencies.json"

        // Contract of `octopus-license-management`'s `processLicensedDependencies` task — owned by that plugin
        private const val LICENSE_DEPS_OUTPUT = "build/dependencies.json"
    }
}
