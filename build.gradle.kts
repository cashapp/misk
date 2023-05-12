import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

// Can be removed once gradle is 8.1
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.dependencyAnalysisPlugin)
  alias(libs.plugins.dokkaGradlePlugin)
  alias(libs.plugins.kotlinAllOpenPlugin) apply false
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.kotlinJpaPlugin) apply false
  alias(libs.plugins.kotlinNoArgPlugin) apply false
  alias(libs.plugins.mavenPublishGradlePlugin) apply false
  alias(libs.plugins.protobufGradlePlugin) apply false
  alias(libs.plugins.wireGradlePlugin) apply false
}

dependencyAnalysis {
  issues {
    all {
      onAny {
        severity("fail")
        // Due to kotlin 1.8.20 see https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/884
        exclude("() -> java.io.File?")
      }
      onUnusedDependencies {
        // Can likely be removed once wire is using 1.7
        exclude("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
      }
    }
    project(":misk-aws2-dynamodb-testing") {
      onAny {
        exclude("org.antlr:antlr4-runtime")
      }
    }
    project(":misk-gcp") {
      onUsedTransitiveDependencies {
        // Can be removed once dd-trace-ot uses 0.33.0 of open tracing.
        exclude("io.opentracing:opentracing-util:0.32.0")
        exclude("io.opentracing:opentracing-noop:0.33.0")
      }
      onRuntimeOnly {
        exclude("com.datadoghq:dd-trace-ot:1.12.1")
      }
    }
    project(":misk-grpc-tests") {
      onUnusedDependencies {
        exclude("javax.annotation:javax.annotation-api:1.3.2")
      }
    }
    project(":misk-jooq") {
      onIncorrectConfiguration {
        exclude("org.jooq:jooq:3.15.0")
      }
    }
  }
}

val testShardNonHibernate: Task by tasks.creating {
  group = "Continuous integration"
  description = "Runs all tests that don't depend on misk-hibernate. " +
    "This target is intended for manually sharding tests to make CI faster."
}

val testShardHibernate: Task by tasks.creating {
  group = "Continuous integration"
  description = "Runs all tests that depend on misk-hibernate. " +
    "This target is intended for manually sharding tests to make CI faster."
}

subprojects {
  buildscript {
    repositories {
      mavenCentral()
    }
  }

  repositories {
    mavenCentral()
    maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
  }

  // Only apply if the project has the kotlin plugin added:
  plugins.withType<KotlinPluginWrapper> {
    tasks.withType<KotlinCompile> {
      kotlinOptions {
        jvmTarget = "11"
      }
    }
    tasks.withType<JavaCompile> {
      sourceCompatibility = "11"
      targetCompatibility = "11"
    }

    dependencies {
      add("testRuntimeOnly", libs.junitEngine)

      // Platform/BOM dependencies constrain versions only.
      // Enforce misk-bom -- it should take priority over external BOMs.
      add("api", enforcedPlatform(project(":misk-bom")))
      add("api", platform(libs.grpcBom))
      add("api", platform(libs.guava))
      add("api", platform(libs.jacksonBom))
      add("api", platform(libs.jerseyBom))
      add("api", platform(libs.jettyBom))
      add("api", platform(libs.kotlinBom))
      add("api", platform(libs.nettyBom))
      add("api", platform(libs.prometheusClientBom))
      add("api", platform(libs.wireBom))
      add("api", platform(libs.wispBom))
    }

    tasks.withType<GenerateModuleMetadata> {
      suppressedValidationErrors.add("enforced-platform")
    }
  }

  tasks.withType<DokkaTask>().configureEach {
    if (name == "dokkaGfm") {
      outputDirectory.set(project.file("$rootDir/docs/0.x/${project.name}"))
    }

    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(8)
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

  plugins.withType<BasePlugin> {
    tasks.findByName("check")!!.apply {
      if (listOf(
          "misk-aws",
          "misk-events",
          "misk-jobqueue",
          "misk-jobqueue-testing",
          "misk-jdbc",
          "misk-jdbc-testing",
          "misk-hibernate",
          "misk-hibernate-testing"
        ).contains(project.name)) {
        testShardHibernate.dependsOn(this)
      } else {
        testShardNonHibernate.dependsOn(this)
      }
    }
  }

  if (file("$rootDir/hooks.gradle").exists()) {
    apply(from = file("$rootDir/hooks.gradle"))
  }

  if (!path.startsWith(":samples")) {
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
    apply(from = "$rootDir/gradle-mvn-publish.gradle")
  }

  // Workaround the Gradle bug resolving multiplatform dependencies.
  // https://github.com/square/okio/issues/647
  configurations.all {
    if (name.contains("kapt") || name.contains("wire") || name.contains("proto") || name.contains("Proto")) {
      attributes.attribute(
        Usage.USAGE_ATTRIBUTE,
        this@subprojects.objects.named(Usage::class, Usage.JAVA_RUNTIME)
      )
    }
  }
}
