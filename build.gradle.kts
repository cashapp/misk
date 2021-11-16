import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
    jcenter()
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
    classpath(Dependencies.kotlinBinaryCompatibilityPlugin)
  }
}

val testShardNonHibernate by tasks.creating() {
  group = "Continuous integration"
  description = "Runs all tests that don't depend on misk-hibernate. " +
          "This target is intended for manually sharding tests to make CI faster."
}

val testShardHibernate by tasks.creating() {
  group = "Continuous integration"
  description = "Runs all tests that depend on misk-hibernate. " +
          "This target is intended for manually sharding tests to make CI faster."
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "org.jetbrains.dokka")

  buildscript {
    repositories {
      mavenCentral()
      jcenter()
    }
  }
  repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
  }

  val compileKotlin by tasks.getting(KotlinCompile::class) {
    kotlinOptions {
      jvmTarget = "1.8"

      // TODO(alec): Enable again once Environment enum is deleted
       allWarningsAsErrors = false
    }
  }
  val compileTestKotlin by tasks.getting(KotlinCompile::class) {
    kotlinOptions {
      jvmTarget = "1.8"

      // TODO(alec): Enable again once Environment enum is deleted
       allWarningsAsErrors = false
    }
  }

  dependencies {
    add("testImplementation", Dependencies.junitApi)
    add("testRuntimeOnly", Dependencies.junitEngine)

    // Platform/BOM dependencies constrain versions only.
    add("api", platform(Dependencies.grpcBom))
    add("api", platform(Dependencies.guava))
    add("api", platform(Dependencies.jacksonBom))
    add("api", platform(Dependencies.jerseyBom))
    add("api", platform(Dependencies.jettyBom))
    add("api", platform(Dependencies.kotlinBom))
    add("api", platform(Dependencies.nettyBom))
  }

  tasks.withType<DokkaTask>().configureEach {
    val dokkaTask = this
    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(8)
      if (dokkaTask.name == "dokkaGfm") {
        outputDirectory.set(project.file("$rootDir/docs/0.x"))
      }
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
  if (file("$rootDir/hooks.gradle").exists()) {
    apply(from = file("$rootDir/hooks.gradle"))
  }

  val checkTask = tasks.findByName("check")
  if (checkTask != null) {
    if (listOf("misk-aws","misk-events","misk-jobqueue","misk-jobqueue-testing","misk-jdbc","misk-jdbc-testing","misk-hibernate","misk-hibernate-testing").contains(name)) {
      testShardHibernate.dependsOn(checkTask)
    } else {
      testShardNonHibernate.dependsOn(checkTask)
    }
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
      attributes.attribute(Usage.USAGE_ATTRIBUTE, this@subprojects.objects.named(Usage::class, Usage.JAVA_RUNTIME))
    }
  }
}

