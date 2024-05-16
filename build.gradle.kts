import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.IOException
import java.net.Socket

buildscript {
  repositories {
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
  }

  dependencies {
    classpath(libs.kotlinAllOpenPlugin)
    classpath(libs.kotlinGradlePlugin)
    classpath(libs.detektGradlePlugin)
    classpath(libs.dokkaGradlePlugin)
    classpath(libs.kotlinNoArgPlugin)
    classpath(libs.junitGradlePlugin)
    classpath(libs.mavenPublishGradlePlugin)
    classpath(libs.protobufGradlePlugin)
    classpath(libs.jgit)
    classpath(libs.wireGradlePlugin)
    classpath(libs.sqldelightGradlePlugin)
  }
}

plugins {
  id("com.autonomousapps.dependency-analysis") version libs.versions.dependencyAnalysisPlugin.get()
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.kotlinBinaryCompatibilityPlugin.get()
}

apply(plugin = "com.vanniktech.maven.publish.base")

allprojects {
  group = when {
    project.path.startsWith(":wisp") -> "app.cash.wisp"
    else -> "com.squareup.misk"
  }
  version = project.findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"
}

dependencyAnalysis {
  issues {
    all {
      ignoreSourceSet("testFixtures")
      ignoreSourceSet("test")
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
  ignoredProjects.addAll(subprojects.map { it.name }.filter { setOf(
    "exemplar",
    "exemplarchat",
    "detektive",
  ).contains(it) }.toList())
  additionalSourceSets.addAll(listOf("testFixtures"))
}

val testShardNonHibernate by tasks.creating {
  group = "Continuous integration"
  description = "These tests don't have shared infra and can run in parallel"
}

val testShardRedis by tasks.creating {
  group = "Continuous integration"
  description = "These tests use redis and thus can't run in parallel"
}

val testShardHibernate by tasks.creating {
  group = "Continuous integration"
  description = "These tests use a DB and thus can't run in parallel"
}

val hibernateProjects = listOf(
  "misk-aws",
  "misk-events",
  "misk-jobqueue",
  "misk-jobqueue-testing",
  "misk-jdbc",
  "misk-jdbc-testing",
  "misk-hibernate",
  "misk-hibernate-testing",
  "misk-rate-limiting-bucket4j-mysql",
  "misk-sqldelight"
)

val redisProjects = listOf(
  "misk-redis",
  "misk-rate-limiting-bucket4j-redis"
)

val detektConfig = "$projectDir/detekt.yaml"

subprojects {
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "io.gitlab.arturbosch.detekt")

  if (!listOf(
      "detektive",
      "exemplar",
      "exemplarchat",
      "misk-bom"
    ).contains(name)
  ) {
    extensions.configure(DetektExtension::class) {
      parallel = true
      buildUponDefaultConfig = false
      ignoreFailures = false
      autoCorrect = true
      config.setFrom(files(detektConfig))
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
    testLogging {
      events("started", "passed", "skipped", "failed")
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }
    dependsOn(":startRedis")
  }

  tasks.withType<Detekt> {
    dependsOn(":detektive:assemble")
    exclude { it.file.absolutePath.contains("/generated/source/") || it.file.absolutePath.contains("SampledLogger") }
  }

  plugins.withType<BasePlugin> {
    tasks.findByName("check")!!.apply {
      if (hibernateProjects.contains(project.name)) {
        testShardHibernate.dependsOn(this)
      } else if (redisProjects.contains(project.name)) {
        testShardRedis.dependsOn(this)
      } else {
        testShardNonHibernate.dependsOn(this)
      }

      // Disable the default `detekt` task and enable `detektMain` which has type resolution enabled
      dependsOn(dependsOn.filterNot { name != "detekt" })
      if (tasks.findByName("detektMain") != null) {
        dependsOn("detektMain")
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
