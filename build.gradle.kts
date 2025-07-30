import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.IOException
import java.net.Socket
import kotlin.time.Duration.Companion.seconds

plugins {
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.binaryCompatibilityValidator)
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.flyway) apply false
  alias(libs.plugins.jooq) apply false
  alias(libs.plugins.kotlinAllOpen) apply false
  alias(libs.plugins.kotlinJpa) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.mavenPublishBase) apply false
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.sqldelight) apply false
  alias(libs.plugins.wire) apply false
}

dependencyAnalysis {
  issues {
    all {
      // TODO do a single pass to cleanup tests / testFixtures deps and then remove this block
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
    project(":misk") {
      onIncorrectConfiguration {
        // For backwards compatibility, we want Moshi classes moved to misk-moshi to still be
        // part of misk api.
        exclude(":misk-moshi")
      }
    }
    project(":misk-core") {
      onUnusedDependencies {
        // For backwards compatibility to prevent existing misk-core consumers from breaking while classes
        //    are moved out to smaller modules.
        exclude(":misk-backoff")
        exclude(":misk-logging")
        exclude(":misk-sampling")
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

val testShardHibernate = tasks.register("testShardHibernate") {
  group = "Continuous integration"
  description = "These tests use a DB and thus can't run in parallel"
}

val testShardMiskAws = tasks.register("testShardMiskAws") {
  group = "Continuous integration"
  description = "Misk AWS tests"
}

val testShardMiskHibernate = tasks.register("testShardMiskHibernate") {
  group = "Continuous integration"
  description = "misk-hibernate tests"
}

val testShardMiskJdbc = tasks.register("testShardMiskJdbc") {
  group = "Continuous integration"
  description = "Misk JDBC tests"
}

val testShardSchemaMigratorGradlePlugin = tasks.register("testShardSchemaMigratorGradlePlugin") {
  group = "Continuous integration"
  description = "Misk JDBC tests"
}

val testShardVitess = tasks.register("testShardVitess") {
  group = "Continuous integration"
  description = "These tests stand-up a lot of Vitess containers and are run in an isolated shard to reduce test times"
}

val testShardVitessGradlePlugin = tasks.register("testShardVitessGradlePlugin") {
  group = "Continuous integration"
  description = "These tests stand-up a Vitess container and are run in an isolated shard to reduce test times"
}

val testShardRedis = tasks.register("testShardRedis") {
  group = "Continuous integration"
  description = "These tests use redis and thus can't run in parallel"
}

val testShardNonHibernate = tasks.register("testShardNonHibernate") {
  group = "Continuous integration"
  description = "These tests don't have shared infra and can run in parallel"
}

val hibernateProjects = listOf(
  "misk-events",
  "misk-jobqueue",
  "misk-jobqueue-testing",
  "misk-jdbc-testing",
  "misk-hibernate-testing",
  "misk-rate-limiting-bucket4j-mysql",
  "misk-sqldelight"
)

val redisProjects = listOf(
  "misk-redis",
  "misk-redis-lettuce",
  "misk-rate-limiting-bucket4j-redis"
)

val detektConfig = file("detekt.yaml")
val doNotDetekt = listOf(
  "detektive",
  "exemplar",
  "exemplarchat",
  "misk-bom",
)

val publishMiskToMavenCentral = tasks.register("publishMiskToMavenCentral")
val publishWispToMavenCentral = tasks.register("publishWispToMavenCentral")

val publishUrl = System.getProperty("publish_url")
val hasPublishUrl = !publishUrl.isNullOrBlank()
if (hasPublishUrl) {
  publishMiskToMavenCentral.configure {
    doFirst {
      error("Cannot publish Misk to Maven Central with a publish_url specified")
    }
  }
  publishWispToMavenCentral.configure {
    doFirst {
      error("Cannot publish Wisp to Maven Central with a publish_url specified")
    }
  }
}

subprojects {
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "io.gitlab.arturbosch.detekt")
  apply(plugin = "com.autonomousapps.dependency-analysis")

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
    tasks.withType<KotlinCompile>().configureEach {
      compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
      }
    }
    tasks.withType<JavaCompile>().configureEach {
      sourceCompatibility = "11"
      targetCompatibility = "11"
    }

    dependencies {
      add("testRuntimeOnly", rootProject.libs.junitEngine)
      add("testRuntimeOnly", rootProject.libs.junitLauncher)

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

    tasks.withType<GenerateModuleMetadata>().configureEach {
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

  tasks.withType<Test>().configureEach {
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

  tasks.withType<Detekt>().configureEach {
    dependsOn(":detektive:assemble")
    exclude { it.file.absolutePath.contains("/generated/source/") || it.file.absolutePath.contains("SampledLogger") }
  }

  plugins.withType<BasePlugin> {
    val subproj = project
    if (hibernateProjects.contains(project.name)) {
      testShardHibernate.configure { dependsOn("${subproj.path}:check") }
    } else if (project.name.equals("misk-aws")) {
      testShardMiskAws.configure { dependsOn("${subproj.path}:check") }
    } else if (project.name.equals("misk-hibernate")) {
      testShardMiskHibernate.configure { dependsOn("${subproj.path}:check") }
    } else if (project.name.equals("misk-jdbc")) {
      testShardMiskJdbc.configure { dependsOn("${subproj.path}:check") }
    } else if (project.name.equals("misk-schema-migrator-gradle-plugin")) {
      testShardSchemaMigratorGradlePlugin.configure { dependsOn("${subproj.path}:check") }
    } else if (project.name.equals("misk-vitess")) {
      testShardVitess.configure { dependsOn("${subproj.path}:check") }
    } else if (project.name.equals("misk-vitess-database-gradle-plugin")) {
      testShardVitessGradlePlugin.configure { dependsOn("${subproj.path}:check") }
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
  configurations.configureEach {
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

subprojects {
  plugins.withId("com.vanniktech.maven.publish.base") {
    if (hasPublishUrl) {
      configure<PublishingExtension> {
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
      configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()
      }
      when (group) {
        "app.cash.wisp" -> publishWispToMavenCentral
        "com.squareup.misk" -> publishMiskToMavenCentral
        else -> error("Unknown group $group in project $path")
      }.configure {
        dependsOn(tasks.named("publishToMavenCentral"))
      }
    }
    configure<MavenPublishBaseExtension> {
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
    val redisVersion = "7.2"
    val redisPort = System.getenv("REDIS_PORT")?.toInt() ?: 6379
    val redisContainerName = "miskTestRedis-$redisPort"
    val redisImage = "public.ecr.aws/docker/library/redis:$redisVersion"

    val portIsOccupied = try {
      Socket("localhost", redisPort).close()
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

tasks.register<StartRedisTask>("startRedis") {
  group = "other"
  description = "Ensures a Redis instance is available; " +
    "starts a redis docker container if there isn't something already there."
  rootDir.set(project.rootDir)
}

abstract class StartRedisClusterTask @Inject constructor(
  @get:Internal
  val execOperations: ExecOperations
) : DefaultTask() {
  @get:Internal
  abstract val rootDir: DirectoryProperty

  @TaskAction
  fun startRedisCluster() {
    val redisVersion = "7.0.10"
    val redisSeedPort = System.getenv("REDIS_CLUSTER_SEED_PORT")?.toInt() ?: 7000
    val redisContainerName = "miskTestRedisCluster-$redisSeedPort"
    val redisImage = "grokzen/redis-cluster:$redisVersion"

    val portIsOccupied = try {
      Socket("localhost", redisSeedPort).close()
      true
    } catch (e: IOException) {
      false
    }
    if (portIsOccupied) {
      logger.info("Port $redisSeedPort is bound, assuming Redis Cluster is already running")
      return
    }

    logger.info("Attempting to start Redis Cluster docker image $redisImage on seed port $redisSeedPort...")
    val dockerArguments = arrayOf(
      "docker", "run",
      "--detach",
      "--rm",
      "--name", redisContainerName,
      "-e", "IP=0.0.0.0",
      "-e", "INITIAL_PORT=$redisSeedPort",
      "-e", "MASTERS=3",
      "-e", "SLAVES_PER_MASTER=1",
      "-p", "7000-7005:7000-7005",
      redisImage
    )
    execOperations.exec {
      workingDir(rootDir.get().asFile)
      commandLine(*dockerArguments)
    }

    waitForRedisCluster(redisContainerName,redisSeedPort)

    logger.info("Started Redis Cluster docker image $redisImage on port $redisSeedPort")
  }

  private fun waitForRedisCluster(containerName:String, port:Int){
    println("Waiting for Redis cluster to become available...")
    val deadline = System.currentTimeMillis() + 60.seconds.inWholeMilliseconds

    fun clusterReady(): Boolean {
      try {
        val process = ProcessBuilder("docker", "exec", containerName,
          "redis-cli", "-c", "-p", port.toString(), "cluster", "info")
          .redirectErrorStream(true)
          .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(5, TimeUnit.SECONDS)

        return "cluster_state:ok" in output && "slots_assigned:16384" in output
      } catch (e: Exception) {
        return false
      }
    }

    while (System.currentTimeMillis() < deadline) {
      if (clusterReady()) {
        println("✅ Redis Cluster is ready.")
        return
      }
      Thread.sleep(1000)
    }

    throw GradleException("Redis Cluster did not become ready within timeout.")
  }
}

tasks.register<StartRedisClusterTask>("startRedisCluster") {
  group = "other"
  description = "Ensures a Redis Cluster instance is available: " +
    "starts a redis cluster docker container if there isn't something already there."
  rootDir.set(project.rootDir)
}
