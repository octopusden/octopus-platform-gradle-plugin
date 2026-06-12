# octopus-platform-gradle-plugin

A thin **aggregator** Gradle plugin that applies common plugins used in a single declaration. The platform plugin has no DSL and no tasks of its own — consumers configure each constituent via its own native extension.

## Constituent plugins (pinned)

| Plugin ID                                                                                                                                                                              |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`org.octopusden.octopus-build-integration`](https://github.com/octopusden/octopus-build-integration-gradle-plugin) — exposes `exportDependencies` task                                |
| [`org.octopusden.octopus-publishing`](https://github.com/octopusden/octopus-publishing-gradle-plugin) — wires `artifactoryPublish` into `publish`                                      |
| [`org.octopusden.octopus.license-management`](https://github.com/octopusden/octopus-license-gradle-plugin) — exposes `processLicenses` / `processLicensedDependencies` (+ node tasks)  |
| [`org.sonarqube`](https://plugins.gradle.org/plugin/org.sonarqube) — exposes `sonar` task                                                                                              |

Versions are pinned at compile time and updated via dependency bot. There is no runtime override mechanism.

## Requirements

| Audience              | JDK                         | Gradle                   |
|-----------------------|-----------------------------|--------------------------|
| Building this plugin  | 25 (toolchain)              | 9.0+ (tested with 9.5.1) |
| Consuming this plugin | 17, 21 (supported); 25 [^1] | 9.0+ (tested with 9.5.1) |

[^1]: JDK 25 is not yet explicitly listed by SonarSource as a supported scanner
runtime, but in practice it works: our FT suite passes on JDK 25, and a real
`sonar` task against SonarQube 25.4+ succeeds because the **server auto-provisions**
a JDK 17 analyzer JVM (look for `JRE provisioning` in the scanner log). If your
SonarQube server has JRE provisioning disabled or is older than 10.6, run the
`sonar` task on JDK 17 or 21 instead.

The full FT suite runs against all three JDKs locally — to reproduce:

```sh
./gradlew :ft:test -Pft.test.jdk=17
./gradlew :ft:test -Pft.test.jdk=21
./gradlew :ft:test -Pft.test.jdk=25
```

## Applying the plugin

The plugin **must be applied on the root project**. Applying it on a subproject fails fast with a clear error message. Reason:
- `octopus-publishing` plugin uses `afterEvaluate` during its own apply, which cannot run on an already-evaluated root project.
- `sonar` plugin applies to the whole build and is not designed to be applied on subprojects.

### `build.gradle.kts` (Kotlin DSL, root project)

```kotlin
plugins {
    id("org.octopusden.octopus-platform")
}
```

### `build.gradle` (Groovy DSL, root project)

```groovy
plugins {
    id 'org.octopusden.octopus-platform'
}
```

### `settings.gradle.kts` (Kotlin DSL)

```kotlin
pluginManagement {
    plugins {
        id("org.octopusden.octopus-platform") version settings.extra["octopus-platform.version"] as String
    }
}
```

### `settings.gradle` (Groovy DSL)

```groovy
pluginManagement {
    plugins {
        id 'org.octopusden.octopus-platform' version settings['octopus-platform.version']
    }
}
```

### Using Version Catalogs (optional)

If your build already uses a [version catalog](https://docs.gradle.org/current/userguide/version_catalogs.html), declare the plugin in `libs.versions.toml`:

```toml
[versions]
octopus-platform = "<version>"

[plugins]
octopus-platform = { id = "org.octopusden.octopus-platform", version.ref = "octopus-platform" }
```

Then apply it from the root build script:

**`build.gradle.kts` (Kotlin DSL)**

```kotlin
plugins {
    alias(libs.plugins.octopus.platform)
}
```

**`build.gradle` (Groovy DSL)**

```groovy
plugins {
    alias(libs.plugins.octopus.platform)
}
```

#### Overriding the version at build time

For monorepo / CI scenarios where the plugin version is built in the same pipeline, override the catalog entry from the settings file:

**`settings.gradle.kts` (Kotlin DSL)**

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
            providers.gradleProperty("octopus-platform.version").orNull?.let { v ->
                version("octopus-platform", v)
            }
        }
    }
}
```

**`settings.gradle` (Groovy DSL)**

```groovy
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            def octopusPlatformVersion = providers.gradleProperty("octopus-platform.version").orNull
            if (octopusPlatformVersion != null) {
                version("octopus-platform", octopusPlatformVersion)
            }
        }
    }
}
```

### Legacy application (`buildscript` + `apply plugin:`)

> **Note:** prefer the `plugins {}` DSL above. This form is provided only for older builds that cannot migrate yet (e.g. mixed Groovy scripts already on `buildscript { classpath ... }`)

Root `build.gradle` (Groovy DSL):

```groovy
buildscript {
    dependencies {
        classpath "org.octopusden.octopus.platform:octopus-platform-gradle-plugin:${project.findProperty('octopus-platform.version')}"
    }
}

apply plugin: 'org.octopusden.octopus-platform'
```

After application, the following tasks are available on the root project:

- `exportDependencies` — from `octopus-build-integration`
- `artifactoryPublish` — from `octopus-publishing`
- `processLicenses`, `processLicensedDependencies` (and `processModuleLicenses`, `processModuleLicensedDependencies`, plus node-license variants) — from `octopus-license-management`
- `sonar` — from `org.sonarqube`

## Configuring constituent plugins

Since the platform plugin has no DSL, configure each constituent through its native extension as usual:

```kotlin
// octopus-build-integration
buildIntegration {
    dependencies {
        // ...
    }
}

// octopus-publishing (uses ARTIFACTORY_URL env var + ARTIFACTORY_DEPLOYER_* credentials)
// see https://github.com/octopusden/octopus-publishing-gradle-plugin for full surface

// octopus-license-management
licenseManagement {
    // includePattern / excludePattern (configuration name regexes)
    // see https://github.com/octopusden/octopus-license-gradle-plugin for full surface
}

// org.sonarqube
sonar {
    properties {
        property("sonar.projectKey", "my-project")
        property("sonar.host.url", "http://localhost:9000")
    }
}
```

## Idempotency & root-only enforcement

- Re-applying the plugin on the root project is a no-op (guarded by an `extraProperties` marker `setupOctopusPlatform`).
- Applying the plugin on a subproject throws a `GradleException` with a message pointing to the root project.

## Disabling a constituent

Each constituent can be individually opted out via a Gradle property. Defaults to `true` (all four applied).

| Property                                          | Effect when set to `false`                |
|---------------------------------------------------|-------------------------------------------|
| `octopus-platform.build-integration.enabled`      | skip `octopus-build-integration` apply    |
| `octopus-platform.publishing.enabled`             | skip `octopus-publishing` apply           |
| `octopus-platform.license-management.enabled`     | skip `octopus-license-management` apply   |
| `octopus-platform.sonar.enabled`                  | skip `org.sonarqube` apply                |
