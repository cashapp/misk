import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
  alias(libs.plugins.protobufGradlePlugin) apply false
  alias(libs.plugins.mavenPublishGradlePlugin) apply false
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

subprojects {
  buildscript {
    repositories {
      mavenCentral()
      gradlePluginPortal()
    }
  }

  repositories {
    mavenCentral()
    maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
  }

  if (!path.startsWith(":wisp-bom")) {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = rootProject.project.libs.plugins.kotlinBinaryCompatibilityPlugin.get().pluginId)
    apply(plugin = rootProject.project.libs.plugins.protobufGradlePlugin.get().pluginId)
    apply(plugin = rootProject.project.libs.plugins.mavenPublishGradlePlugin.get().pluginId)

    configure<JavaPluginExtension> {
      withSourcesJar()
      withJavadocJar()
    }

    plugins.withId("com.vanniktech.maven.publish.base") {
      val publishingExtension = extensions.getByType(PublishingExtension::class.java)
      configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        pomFromGradleProperties()
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.DEFAULT, true)
        signAllPublications()
      }

      publishingExtension.publications.create<MavenPublication>("maven") {
        from(components["java"])
      }
    }

  }

  apply(plugin = "version-catalog")

  // Only apply if the project has the kotlin plugin added:
  plugins.withType<KotlinPluginWrapper> {
    val compileKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
      }
    }
    val compileTestKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
      }
    }

    dependencies {
      add("testImplementation", project.rootProject.libs.junitApi)
      add("testRuntimeOnly", project.rootProject.libs.junitEngine)

      // Platform/BOM dependencies constrain versions only.
      // Enforce wisp-bom -- it should take priority over external BOMs.
      add("api", enforcedPlatform(project(":wisp-bom")))
      add("api", platform(project.rootProject.libs.grpcBom))
      add("api", platform(project.rootProject.libs.jacksonBom))
      add("api", platform(project.rootProject.libs.nettyBom))

      // The kotlin API surface used in this library is not exposed via
      // the external API, so we shouldn't be forcing downstream consumers
      // to use a particular kotlin version. Doing this can cause compilation
      // failures for downstream repositories that want to use wisp APIs
      // in their buildscripts or plugins but still want to use an older
      // version of the kotlin compiler. Use 'implementation' instead of 'api'
      // so that we're not forcing downstream consumers to adopt our kotlin
      // BOM versions.
      add("implementation", platform(project.rootProject.libs.kotlinBom))
    }

    tasks.withType<GenerateModuleMetadata> {
      suppressedValidationErrors.add("enforced-platform")
    }
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
      events("started", "passed", "skipped", "failed")
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }
  }

  apply(plugin = "com.github.ben-manes.versions")

  tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    revision = "release"
    resolutionStrategy {
      componentSelection {
        all {
          if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
            reject("Release candidate")
          }
        }
      }
    }
  }

}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

// this needs to be defined here for the versionCatalogUpdate
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
  revision = "release"
  resolutionStrategy {
    componentSelection {
      all {
        if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
          reject("Release candidate")
        }
      }
    }
  }
}

versionCatalogUpdate {
  /**
   * Use @pin and @keep in gradle/lib.versions.toml instead of defining here
   */
  sortByKey.set(true)
}
