package org.octopusden.octopus.platform.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.LoggerFactory

class OctopusPlatformPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (project != project.rootProject) {
            throw GradleException(
                "octopus-platform-gradle-plugin must be applied on the root project, " +
                    "but was applied on subproject '${project.path}'. " +
                    "Move `id(\"org.octopusden.octopus-platform\")` into the root build.gradle(.kts)."
            )
        }

        val rootExtras = project.extensions.extraProperties
        if (rootExtras.has(SETUP_MARKER)) {
            LOGGER.debug("octopus-platform already configured on {}, skipping", project)
            return
        }
        rootExtras.set(SETUP_MARKER, true)

        LOGGER.info("Applying octopus-platform-gradle-plugin to root project {}", project)

        val buildIntegrationEnabled = isConstituentEnabled(project, "build-integration")
        val publishingEnabled = isConstituentEnabled(project, "publishing")
        val licenseManagementEnabled = isConstituentEnabled(project, "license-management")
        val sonarEnabled = isConstituentEnabled(project, "sonar")

        if (buildIntegrationEnabled) project.pluginManager.apply(BUILD_INTEGRATION_PLUGIN_ID)
        if (publishingEnabled) project.pluginManager.apply(PUBLISHING_PLUGIN_ID)
        if (licenseManagementEnabled) project.pluginManager.apply(LICENSE_MANAGEMENT_PLUGIN_ID)
        if (sonarEnabled) project.pluginManager.apply(SONARQUBE_PLUGIN_ID)

        LOGGER.info(
            "octopus-platform applied: build-integration={}, publishing={}, license-management={}, sonar={}",
            buildIntegrationEnabled, publishingEnabled, licenseManagementEnabled, sonarEnabled,
        )
    }

    private fun isConstituentEnabled(project: Project, key: String): Boolean {
        val propName = "octopus-platform.$key.enabled"
        val raw = project.findProperty(propName)?.toString() ?: return true
        return when (raw.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> {
                LOGGER.warn(
                    "Unrecognised value '{}' for property '{}' — expected 'true' or 'false'. " +
                        "Falling back to default (enabled).",
                    raw, propName,
                )
                true
            }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(OctopusPlatformPlugin::class.java)

        private const val SETUP_MARKER = "setupOctopusPlatform"

        const val BUILD_INTEGRATION_PLUGIN_ID = "org.octopusden.octopus-build-integration"
        const val PUBLISHING_PLUGIN_ID = "org.octopusden.octopus-publishing"
        const val LICENSE_MANAGEMENT_PLUGIN_ID = "org.octopusden.octopus.license-management"
        const val SONARQUBE_PLUGIN_ID = "org.sonarqube"
    }
}
