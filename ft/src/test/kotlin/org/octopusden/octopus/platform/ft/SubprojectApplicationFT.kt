package org.octopusden.octopus.platform.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Applying the platform plugin on anything other than the root project
 * must fail fast with a clear, actionable error message.
 */
class SubprojectApplicationFT {

    @Test
    @DisplayName("apply on subproject fails with a clear error message (plugins { id(...) } form)")
    fun testApplicationOnSubprojectFailsFast() {
        assertSubprojectApplicationFails(fixture = "subproject-only")
    }

    @Test
    @DisplayName("apply on subproject fails with a clear error message (imperative apply(plugin = ...) form)")
    fun testApplicationOnSubprojectFailsFast_imperativeApply() {
        assertSubprojectApplicationFails(fixture = "subproject-only-imperative")
    }

    private fun assertSubprojectApplicationFails(fixture: String) {
        val result = runGradle {
            testProjectName = fixture
            tasks = listOf("help")
        }
        assertThat(result.instance.exitCode)
            .withFailMessage("Expected non-zero exit code, stderr:\n%s", result.stderr.joinToString("\n"))
            .isNotEqualTo(0)

        val combined = (result.stdout + result.stderr).joinToString("\n")
        assertThat(combined).contains("octopus-platform-gradle-plugin must be applied on the root project")
        assertThat(combined).contains(":child")
    }
}
