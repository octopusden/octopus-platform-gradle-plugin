package org.octopusden.octopus.platform.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The platform plugin has no DSL; constituent plugin extensions
 * (`octopusPublishing`, `buildIntegration`, `sonar`) must remain
 * configurable by consumers after the platform plugin is applied.
 */
class ConstituentConfigFT {

    @Test
    @DisplayName("sonar extension remains configurable post-apply")
    fun testSonarExtensionOverrideStillWorks() {
        val result = runGradle {
            testProjectName = "constituent-config"
            tasks = listOf("verifyConfig", "-q")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains("sonar.skipProject=true")
    }
}
