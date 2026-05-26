package org.octopusden.octopus.platform.ft

import com.platformlib.process.api.ProcessInstance
import com.platformlib.process.factory.ProcessBuilders
import com.platformlib.process.local.builder.LocalProcessBuilder
import com.platformlib.process.local.specification.LocalProcessSpec
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

/**
 * Lambda-DSL harness that copies a packaged test project from
 * `src/test/resources/projects/<name>` into a temp directory, drops the
 * parent's `gradlew` wrapper into it, and runs the requested tasks.
 *
 * Adapted from `octopus-publishing-gradle-plugin`'s FT harness.
 */
class TestGradleRun {
    lateinit var testProjectName: String
    var tasks: List<String> = emptyList()
    var additionalArguments: List<String> = emptyList()
    var additionalEnvVariables: Map<String, String> = emptyMap()
    var additionalProperties: Map<String, String> = emptyMap()
}

data class GradleRunResult(
    val instance: ProcessInstance,
    val projectPath: Path,
    val stdout: List<String>,
    val stderr: List<String>,
)

private val logger = LoggerFactory.getLogger("org.octopusden.octopus.platform.ft.GradleRunner")

fun runGradle(init: TestGradleRun.() -> Unit): GradleRunResult {
    val config = TestGradleRun().apply(init)

    val templatePath = locateResource("/projects/${config.testProjectName}")
    val projectPath = Files.createTempDirectory("platform-ft-${config.testProjectName}-")
    copyDirectory(templatePath, projectPath)

    val wrapperSource = locateWrapperRoot()
    copyWrapper(wrapperSource, projectPath)

    val pluginVersion = System.getProperty("octopus-platform.version")
        ?: System.getenv("OCTOPUS_PLATFORM_VERSION")
        ?: error("octopus-platform.version is not set")

    val args = mutableListOf<String>()
    args += "--no-daemon"
    args += "-Poctopus-platform.version=$pluginVersion"
    config.additionalProperties.forEach { (k, v) -> args += "-P$k=$v" }
    args += config.tasks
    args += config.additionalArguments

    val env = mutableMapOf<String, String>(
        "JAVA_HOME" to System.getProperty("java.home"),
        "OCTOPUS_PLATFORM_VERSION" to pluginVersion,
    )
    env += config.additionalEnvVariables

    val stdout = mutableListOf<String>()
    val stderr = mutableListOf<String>()

    val processBuilder: LocalProcessBuilder = ProcessBuilders.newProcessBuilder(LocalProcessSpec.LOCAL_COMMAND)
    val instance = processBuilder
        .envVariables(env)
        .logger { it.logger(logger) }
        .mapBatExtension()
        .mapCmdExtension()
        .workDirectory(projectPath)
        .commandAndArguments("$projectPath/gradlew")
        .stdOutConsumer(stdout::add)
        .stdErrConsumer(stderr::add)
        .processInstance { it.unlimited() }
        .build()
        .execute(*args.toTypedArray())
        .toCompletableFuture()
        .join()

    return GradleRunResult(instance, projectPath, stdout, stderr)
}

private fun locateResource(path: String): Path {
    val url = object {}.javaClass.getResource(path)
        ?: error("Test resource '$path' not found on the test classpath")
    return Paths.get(url.toURI())
}

private fun locateWrapperRoot(): Path {
    var dir: Path = Paths.get("").toAbsolutePath()
    repeat(5) {
        if (Files.exists(dir.resolve("gradle/wrapper/gradle-wrapper.jar"))) {
            return dir
        }
        dir = dir.parent ?: return@repeat
    }
    error("Could not locate Gradle wrapper to copy into temp test project")
}

private fun copyWrapper(from: Path, to: Path) {
    Files.createDirectories(to.resolve("gradle/wrapper"))
    Files.copy(
        from.resolve("gradle/wrapper/gradle-wrapper.jar"),
        to.resolve("gradle/wrapper/gradle-wrapper.jar"),
        StandardCopyOption.REPLACE_EXISTING,
    )
    Files.copy(
        from.resolve("gradle/wrapper/gradle-wrapper.properties"),
        to.resolve("gradle/wrapper/gradle-wrapper.properties"),
        StandardCopyOption.REPLACE_EXISTING,
    )
    Files.copy(from.resolve("gradlew"), to.resolve("gradlew"), StandardCopyOption.REPLACE_EXISTING)
    Files.copy(from.resolve("gradlew.bat"), to.resolve("gradlew.bat"), StandardCopyOption.REPLACE_EXISTING)
    try {
        val perms = Files.getPosixFilePermissions(to.resolve("gradlew")).toMutableSet()
        perms += PosixFilePermission.OWNER_EXECUTE
        perms += PosixFilePermission.GROUP_EXECUTE
        perms += PosixFilePermission.OTHERS_EXECUTE
        Files.setPosixFilePermissions(to.resolve("gradlew"), perms)
    } catch (_: UnsupportedOperationException) {
        // Windows — ignore
    }
}

private fun copyDirectory(source: Path, target: Path) {
    Files.walk(source).use { stream ->
        stream.forEach { src ->
            val rel = source.relativize(src)
            val dest = target.resolve(rel.toString())
            if (Files.isDirectory(src)) {
                Files.createDirectories(dest)
            } else {
                Files.createDirectories(dest.parent)
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
