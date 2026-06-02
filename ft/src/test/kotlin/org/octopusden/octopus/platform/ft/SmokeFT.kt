package org.octopusden.octopus.platform.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that applying `id("org.octopusden.octopus-platform")` registers
 * the four constituent plugins' main tasks on the root project.
 */
class SmokeFT {

    @Test
    @DisplayName("applying platform plugin registers exportDependencies, artifactoryPublish, processLicensedDependencies, and sonar tasks")
    fun testTasksRegistered() {
        val result = runGradle {
            testProjectName = "smoke"
            tasks = listOf("tasks", "--all")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains("exportDependencies")
        assertThat(out).contains("artifactoryPublish")
        assertThat(out).contains("processLicensedDependencies")
        assertThat(out).contains("sonar")
    }
}
