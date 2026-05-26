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
        val depsFile = result.projectPath.resolve("build/components-dependencies.json")
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
    @DisplayName("e2e: sonar task graph resolves under --dry-run (no Sonar server required)")
    fun testSonarTaskGraphResolves() {
        val result = runGradle {
            testProjectName = "e2e"
            tasks = listOf("sonar", "--dry-run")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains(":sonar SKIPPED")
    }
}
