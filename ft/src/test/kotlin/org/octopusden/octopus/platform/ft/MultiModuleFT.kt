package org.octopusden.octopus.platform.ft

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * In a multi-module build with the platform plugin applied only at root,
 * every subproject that declares a `MavenPublication` must have its
 * `publish` lifecycle correctly wired and its POM generated.
 */
class MultiModuleFT {

    @Test
    @DisplayName("multi-module: poms generated in every subproject")
    fun testPomsGeneratedInEverySubproject() {
        val result = runGradle {
            testProjectName = "multi-module"
            tasks = listOf(
                "clean",
                ":sub-a:generatePomFileForMavenJavaPublication",
                ":sub-b:generatePomFileForMavenJavaPublication",
            )
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        assertThat(result.projectPath.resolve("sub-a/build/publications/mavenJava/pom-default.xml")).exists()
        assertThat(result.projectPath.resolve("sub-b/build/publications/mavenJava/pom-default.xml")).exists()
    }

    @Test
    @DisplayName("multi-module: artifactoryPublish task exists per subproject")
    fun testArtifactoryPublishWiredPerSubproject() {
        val result = runGradle {
            testProjectName = "multi-module"
            tasks = listOf(":sub-a:tasks", "--all")
        }
        assertEquals(0, result.instance.exitCode, "Gradle execution failure:\n${result.stderr.joinToString("\n")}")

        val out = result.stdout.joinToString("\n")
        assertThat(out).contains("artifactoryPublish")
    }
}
