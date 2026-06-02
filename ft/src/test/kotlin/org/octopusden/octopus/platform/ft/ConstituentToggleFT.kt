package org.octopusden.octopus.platform.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that `-Poctopus-platform.<constituent>.enabled=false` suppresses
 * application of the matching constituent plugin while leaving the others
 * in place. Default behavior (all four enabled) is covered by [SmokeFT].
 */
class ConstituentToggleFT {

    @Test
    @DisplayName("disabling build-integration removes exportDependencies task only")
    fun testBuildIntegrationDisabled() {
        val result = runGradle {
            testProjectName = "smoke"
            tasks = listOf("tasks", "--all")
            additionalProperties = mapOf("octopus-platform.build-integration.enabled" to "false")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).doesNotContain("exportDependencies")
        assertThat(out).contains("artifactoryPublish")
        assertThat(out).contains("processLicensedDependencies")
        assertThat(out).contains("sonar")
    }

    @Test
    @DisplayName("disabling publishing removes artifactoryPublish task only")
    fun testPublishingDisabled() {
        val result = runGradle {
            testProjectName = "smoke"
            tasks = listOf("tasks", "--all")
            additionalProperties = mapOf("octopus-platform.publishing.enabled" to "false")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains("exportDependencies")
        assertThat(out).doesNotContain("artifactoryPublish")
        assertThat(out).contains("processLicensedDependencies")
        assertThat(out).contains("sonar")
    }

    @Test
    @DisplayName("disabling license-management removes processLicensedDependencies task only")
    fun testLicenseManagementDisabled() {
        val result = runGradle {
            testProjectName = "smoke"
            tasks = listOf("tasks", "--all")
            additionalProperties = mapOf("octopus-platform.license-management.enabled" to "false")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains("exportDependencies")
        assertThat(out).contains("artifactoryPublish")
        assertThat(out).doesNotContain("processLicensedDependencies")
        assertThat(out).contains("sonar")
    }

    @Test
    @DisplayName("disabling sonar removes sonar task only")
    fun testSonarDisabled() {
        val result = runGradle {
            testProjectName = "smoke"
            tasks = listOf("tasks", "--all")
            additionalProperties = mapOf("octopus-platform.sonar.enabled" to "false")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains("exportDependencies")
        assertThat(out).contains("artifactoryPublish")
        assertThat(out).contains("processLicensedDependencies")
        assertThat(out).doesNotContain("sonar")
    }
}
