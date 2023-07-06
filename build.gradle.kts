import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
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

apply(plugin = "com.vanniktech.maven.publish.base")

allprojects {
  group = project.property("GROUP") as String
  version = project.findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"
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
        exclude(":misk-testing")
      }
    }
    // False positives.
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
        exclude("org.jooq:jooq:3.18.2")
      }
    }
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
      add("api", platform(Dependencies.guavaBom))
      add("api", platform(Dependencies.guiceBom))
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

  configurations.all {
    // Workaround the Gradle bug resolving multiplatform dependencies.
    // https://github.com/square/okio/issues/647
    if (name.contains("kapt") || name.contains("wire") || name.contains("proto") || name.contains("Proto")) {
      attributes.attribute(
        Usage.USAGE_ATTRIBUTE,
        this@subprojects.objects.named(Usage::class, Usage.JAVA_RUNTIME)
      )
    }

    // Workaround reports of dependency overlaps with Guava.
    // https://github.com/google/guava/releases/tag/v32.1.0
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.guava:listenablefuture") {
      select("com.google.guava:guava:0")
    }
  }
}

allprojects {
  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.S01, automaticRelease = true)
      signAllPublications()
      pom {
        description.set("Open source application container in Kotlin")
        name.set(project.name)
        url.set("https://github.com/cashapp/misk/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          url.set("https://github.com/cashapp/misk/")
          connection.set("scm:git:git://github.com/cashapp/misk.git")
          developerConnection.set("scm:git:ssh://git@github.com/cashapp/misk.git")
        }
        developers {
          developer {
            id.set("square")
            name.set("Square, Inc.")
          }
        }
      }
    }
  }
}
