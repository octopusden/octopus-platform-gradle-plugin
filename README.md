# octopus-platform-gradle-plugin

A thin **aggregator** Gradle plugin that applies common plugins used in a single declaration. The platform plugin has no DSL and no tasks of its own — consumers configure each constituent via its own native extension.

## Constituent plugins (pinned)

| Plugin ID                                                                                                                                               |
|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`org.octopusden.octopus-build-integration`](https://github.com/octopusden/octopus-build-integration-gradle-plugin) — exposes `exportDependencies` task |
| [`org.octopusden.octopus-publishing`](https://github.com/octopusden/octopus-publishing-gradle-plugin) — wires `artifactoryPublish` into `publish`       |
| [`org.sonarqube`](https://plugins.gradle.org/plugin/org.sonarqube) — exposes `sonar` task                                                               |

Versions are pinned at compile time and updated via dependency bot. There is no runtime override mechanism.

## Requirements

| Audience              | JDK                         | Gradle                   |
|-----------------------|-----------------------------|--------------------------|
| Building this plugin  | 25 (toolchain)              | 9.0+ (tested with 9.5.1) |
| Consuming this plugin | 17, 21 (supported); 25 [^1] | 9.0+ (tested with 9.5.1) |

[^1]: JDK 25 is **not** yet officially documented as a supported runtime by
SonarSource (sonar-scanner-gradle 7.3 README still pins build/test to JDK 17;
JDK 21 support landed post-7.3 on master). Our FT suite confirms plugin **apply + task graph resolution** (`sonar --dry-run`) succeeds on JDK 25, but **full
`sonar` analysis** on JDK 25 is not yet endorsed upstream.

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

### `build.gradle.kts` (root project)

```kotlin
plugins {
    id("org.octopusden.octopus-platform")
}
```

### `settings.gradle.kts` 

```kotlin
pluginManagement {
    plugins {
        id("org.octopusden.octopus-platform") version settings.extra["octopus-platform.version"] as String
    }
}
```

After application, the following tasks are available on the root project:

- `exportDependencies` — from `octopus-build-integration`
- `artifactoryPublish` — from `octopus-publishing`
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
