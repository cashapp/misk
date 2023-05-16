import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
  }

  dependencies {
    classpath(Dependencies.kotlinAllOpenPlugin)
    classpath(Dependencies.kotlinGradlePlugin)
    classpath(Dependencies.dokkaGradlePlugin)
    classpath(Dependencies.kotlinNoArgPlugin)
    classpath(Dependencies.junitGradlePlugin)
    classpath(Dependencies.mavenPublishGradlePlugin)
    classpath(Dependencies.protobufGradlePlugin)
    classpath(Dependencies.jgit)
    classpath(Dependencies.wireGradlePlugin)
  }
}

plugins {
  id("com.autonomousapps.dependency-analysis") version Dependencies.dependencyAnalysisPluginVersion
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version Dependencies.kotlinBinaryCompatibilityPluginVersion
}

dependencyAnalysis {
  issues {
    all {
      ignoreSourceSet("testFixtures")
      onAny {
        severity("fail")
        // Due to kotlin 1.8.20 see https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/884
        exclude("() -> java.io.File?")
        exclude("org.jetbrains.kotlin:kotlin-test:1.8.21")
      }
    }
    // False positives.
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
//    project(":misk-jooq") {
//      onIncorrectConfiguration {
//        exclude("org.jooq:jooq:3.18.2")
//      }
//    }
  }
}

apiValidation {
  ignoredProjects.addAll(listOf("exemplar", "exemplarchat"))
  additionalSourceSets.addAll(listOf("testFixtures"))
}

val testShardNonHibernate by tasks.creating {
  group = "Continuous integration"
  description = "Runs all tests that don't depend on misk-hibernate. " +
    "This target is intended for manually sharding tests to make CI faster."
}

val testShardHibernate by tasks.creating {
  group = "Continuous integration"
  description = "Runs all tests that depend on misk-hibernate. " +
    "This target is intended for manually sharding tests to make CI faster."
}

subprojects {
  apply(plugin = "org.jetbrains.dokka")

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
      add("testRuntimeOnly", Dependencies.junitEngine)

      // Platform/BOM dependencies constrain versions only.
      // Enforce misk-bom -- it should take priority over external BOMs.
      add("api", enforcedPlatform(project(":misk-bom")))
      add("api", platform(Dependencies.grpcBom))
      add("api", platform(Dependencies.guava))
      add("api", platform(Dependencies.jacksonBom))
      add("api", platform(Dependencies.jerseyBom))
      add("api", platform(Dependencies.jettyBom))
      add("api", platform(Dependencies.kotlinBom))
      add("api", platform(Dependencies.nettyBom))
      add("api", platform(Dependencies.prometheusClientBom))
      add("api", platform(Dependencies.wireBom))
      add("api", platform(Dependencies.wispBom))
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
