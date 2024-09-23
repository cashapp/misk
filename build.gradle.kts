import com.vanniktech.maven.publish.SonatypeHost
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.IOException
import java.net.Socket

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath(platform(libs.kotlinBom))
    classpath(platform(libs.kotlinGradleBom))
    classpath(libs.detektGradlePlugin)
    classpath(libs.dokkaGradlePlugin)
    // TODO remove Flyway when Misk SchemaMigratorGradlePlugin merges
    classpath(libs.flywayGradlePlugin)
    classpath(libs.jgit)
    classpath(libs.jooqGradlePlugin)
    classpath(libs.kotlinAllOpenPlugin)
    classpath(libs.kotlinGradlePlugin)
    classpath(libs.kotlinNoArgPlugin)
    classpath(libs.mysql)
    classpath(libs.protobufGradlePlugin)
    classpath(libs.sqldelightGradlePlugin)
    classpath(libs.wireGradlePlugin)
  }
}

plugins {
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.binaryCompatibilityValidator)
  alias(libs.plugins.mavenPublishBase)
}

dependencyAnalysis {
  issues {
    all {
      ignoreSourceSet("testFixtures", "test")
      onAny {
        severity("fail")
        exclude("org.jetbrains.kotlin:kotlin-test:1.8.21")
        exclude(":misk-testing")
      }
    }
    all {
      onUnusedDependencies {
        exclude("com.github.docker-java:docker-java-api")
        exclude("org.jetbrains.kotlin:kotlin-stdlib")
      }
      onIncorrectConfiguration {
        exclude("org.jetbrains.kotlin:kotlin-stdlib")
      }
    }
    // False positives.
    project(":misk-gcp") {
      onUsedTransitiveDependencies {
        // Can be removed once dd-trace-ot uses 0.33.0 of open tracing.
        exclude("io.opentracing:opentracing-util")
        exclude("io.opentracing:opentracing-noop")
      }
      onRuntimeOnly {
        exclude("com.datadoghq:dd-trace-ot")
      }
    }
    project(":misk-grpc-tests") {
      onUnusedDependencies {
        exclude("javax.annotation:javax.annotation-api")
      }
    }
    project(":misk-jooq") {
      onIncorrectConfiguration {
        exclude("org.jooq:jooq")
      }
    }
    project(":detektive") {
      onUnusedDependencies {
        exclude("com.google.inject:guice")
      }
    }
    project(":wisp:wisp-logging-testing") {
      onUnusedDependencies {
        // False positive.
        exclude(":wisp:wisp-logging")
      }
    }
    project(":wisp:wisp-rate-limiting:bucket4j") {
      onUnusedDependencies {
        // Plugin does not recognize use of tests artifact from bucket4j's maven manifest
        exclude("com.bucket4j:bucket4j-core")
      }
    }
    project(":misk-action-scopes") {
      onIncorrectConfiguration {
        // For backwards compatibility, we want Action Scoped classes moved to misk-api to still be
        // part of misk-action-scopes api.
        exclude(":misk-api")
      }
    }
  }
}

apiValidation {
  // ignore subprojects only if present. This allows us to activate only a subset
  // of projects with settings.gradle.kts overlay if we want to activate only some subprojects.
  val ignorable = setOf(
    "exemplar",
    "exemplarchat",
    "detektive",
    "misk-schema-migrator-gradle-plugin"
  )
  ignoredProjects.addAll(subprojects.map { it.name }.filter { it in ignorable })
  additionalSourceSets.add("testFixtures")
}

val testShardNonHibernate = tasks.register("testShardNonHibernate") {
  group = "Continuous integration"
  description = "These tests don't have shared infra and can run in parallel"
}

val testShardRedis = tasks.register("testShardRedis") {
  group = "Continuous integration"
  description = "These tests use redis and thus can't run in parallel"
}

val testShardHibernate = tasks.register("testShardHibernate") {
  group = "Continuous integration"
  description = "These tests use a DB and thus can't run in parallel"
}

val hibernateProjects = listOf(
  "misk-aws",
  "misk-events",
  "misk-hibernate",
  "misk-jobqueue",
  "misk-jobqueue-testing",
  "misk-jdbc",
  "misk-jdbc-testing",
  "misk-hibernate-testing",
  "misk-rate-limiting-bucket4j-mysql",
  "misk-sqldelight"
)

val redisProjects = listOf(
  "misk-redis",
  "misk-rate-limiting-bucket4j-redis"
)

val detektConfig = file("detekt.yaml")
val doNotDetekt = listOf(
  "detektive",
  "exemplar",
  "exemplarchat",
  "misk-bom",
)

subprojects {
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "io.gitlab.arturbosch.detekt")

  if (name !in doNotDetekt) {
    extensions.configure(DetektExtension::class) {
      parallel = true
      buildUponDefaultConfig = false
      ignoreFailures = false
      autoCorrect = true
      config.setFrom(detektConfig)
    }
  } else {
    extensions.configure(DetektExtension::class) {
      disableDefaultRuleSets = true
      ignoreFailures = true
    }
  }

  dependencies {
    add("detektPlugins", project(":detektive"))
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
      add("testRuntimeOnly", rootProject.libs.junitEngine)

      // Platform/BOM dependencies constrain versions only.
      // Enforce misk-bom -- it should take priority over external BOMs.
      add("api", enforcedPlatform(project(":misk-bom")))
      add("api", platform(rootProject.libs.grpcBom))
      add("api", platform(rootProject.libs.guavaBom))
      add("api", platform(rootProject.libs.guiceBom))
      add("api", platform(rootProject.libs.jacksonBom))
      add("api", platform(rootProject.libs.jerseyBom))
      add("api", platform(rootProject.libs.jettyBom))
      add("api", platform(rootProject.libs.kotlinBom))
      add("api", platform(rootProject.libs.nettyBom))
      add("api", platform(rootProject.libs.prometheusClientBom))
      add("api", platform(rootProject.libs.tempestBom))
      add("api", platform(rootProject.libs.wireBom))
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

    if (System.getenv("CI") == "true") {
      // Enable Datadog tracing for buildkite tests
      systemProperties(
        mapOf(
          "dd.civisibility.enabled" to true,
          "dd.profiling.enabled" to false,
          "dd.trace.enabled" to true,
          "dd.jmxfetch.enabled" to false,
          "dd.civisibility.code.coverage.enabled" to false,
          "dd.civisibility.git.upload.enabled" to false,
          "dd.integration.opentracing.enabled" to true,
          "dd.instrumentation.telemetry.enabled" to false,
        )
      )
      develocity.testRetry {
        maxRetries.set(1)
        maxFailures.set(5)
      }
    }

    val enableLogging = project.findProperty("misk.test.logging")?.toString().toBoolean()

    if (enableLogging) {
      testLogging {
        events = setOf(STARTED, PASSED, SKIPPED, FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
      }
    } else {
      testLogging {
        showStandardStreams = false
        exceptionFormat = TestExceptionFormat.SHORT
        showExceptions = true
      }
    }
  }

  tasks.withType<Detekt> {
    dependsOn(":detektive:assemble")
    exclude { it.file.absolutePath.contains("/generated/source/") || it.file.absolutePath.contains("SampledLogger") }
  }

  plugins.withType<BasePlugin> {
    val subproj = project
    if (hibernateProjects.contains(project.name)) {
      testShardHibernate.configure { dependsOn("${subproj.path}:check") }
    } else if (redisProjects.contains(project.name)) {
      testShardRedis.configure { dependsOn("${subproj.path}:check") }
    } else {
      testShardNonHibernate.configure { dependsOn("${subproj.path}:check") }
    }

    tasks.named("check") {
      // Disable the default `detekt` task and enable `detektMain` which has type resolution enabled
      dependsOn(dependsOn.filterNot { name != "detekt" })
      dependsOn(tasks.named { it == "detektMain" })
    }
  }

  val configurationNames = setOf("kapt", "wire", "proto", "Proto")
  configurations.all {
    // Workaround the Gradle bug resolving multiplatform dependencies.
    // https://github.com/square/okio/issues/647
    if (name in configurationNames) {
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
    val publishUrl = System.getProperty("publish_url")
    if (!publishUrl.isNullOrBlank()) {
      publishing {
        repositories {
          maven {
            url = uri(publishUrl)
            credentials {
              username = System.getProperty("publish_username", "")
              password = System.getProperty("publish_password", "")
            }
          }
        }
      }
    } else {
      mavenPublishing {
        publishToMavenCentral(SonatypeHost.S01, automaticRelease = true)
        signAllPublications()
      }
    }
    mavenPublishing {
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

  tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }
}

abstract class StartRedisTask @Inject constructor(
  @get:Internal
  val execOperations: ExecOperations
) : DefaultTask() {
  @get:Internal
  abstract val rootDir: DirectoryProperty

  @TaskAction
  fun startRedis() {
    val redisVersion = "6.2"
    val redisPort = System.getenv("REDIS_PORT") ?: "6379"
    val redisContainerName = "miskTestRedis-$redisPort"
    val redisImage = "redis:$redisVersion"

    val portIsOccupied = try {
      Socket("localhost", redisPort.toInt()).close()
      true
    } catch (e: IOException) {
      false
    }
    if (portIsOccupied) {
      logger.info("Port $redisPort is bound, assuming Redis is already running")
      return
    }

    logger.info("Attempting to start Redis docker image $redisImage on port $redisPort...")
    val dockerArguments = arrayOf(
      "docker", "run",
      "--detach",
      "--rm",
      "--name", redisContainerName,
      "-p", "$redisPort:6379",
      redisImage,
      "redis-server",
      "--loglevel debug"
    )
    execOperations.exec {
      workingDir(rootDir.get().asFile)
      commandLine(*dockerArguments)
    }
    logger.info("Started Redis docker image $redisImage on port $redisPort")
  }
}

tasks.register("startRedis", StartRedisTask::class.java) {
  group = "other"
  description = "Ensures a Redis instance is available; " +
    "starts a redis docker container if there isn't something already there."
  rootDir.set(project.rootDir)
}
