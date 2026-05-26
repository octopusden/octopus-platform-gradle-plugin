package org.octopusden.octopus.platform.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Re-applying `id("org.octopusden.octopus-platform")` on the root project
 * must not double-configure or fail.
 */
class IdempotencyFT {

    @Test
    @DisplayName("plugin re-applied on root does not double-configure or fail")
    fun testPluginIsIdempotent() {
        val result = runGradle {
            testProjectName = "idempotency"
            tasks = listOf("clean", ":child:generatePomFileForMavenJavaPublication")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val pomPath = result.projectPath.resolve("child/build/publications/mavenJava/pom-default.xml")
        assertThat(pomPath).exists()
    }
}
