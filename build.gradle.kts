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
    classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.5.0")
    classpath(Dependencies.kotlinAllOpenPlugin)
    classpath(Dependencies.kotlinGradlePlugin)
    classpath(Dependencies.kotlinNoArgPlugin)
    classpath(Dependencies.junitGradlePlugin)
    classpath(Dependencies.mavenPublishGradlePlugin)
    classpath(Dependencies.protobufGradlePlugin)
    classpath(Dependencies.jgit)
    classpath(Dependencies.wireGradlePlugin)
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
    add("api", enforcedPlatform(Dependencies.grpcBom))
    add("api", enforcedPlatform(Dependencies.guava))
    add("api", enforcedPlatform(Dependencies.jacksonBom))
    add("api", enforcedPlatform(Dependencies.jerseyBom))
    add("api", enforcedPlatform(Dependencies.jettyBom))
    add("api", enforcedPlatform(Dependencies.kotlinBom))
    add("api", enforcedPlatform(Dependencies.nettyBom))
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

  val testTask = tasks.findByName("test")
  if (testTask != null) {
    if (listOf(
        "misk-aws",
        "misk-events",
        "misk-jobqueue",
        "misk-jobqueue-testing",
        "misk-jdbc",
        "misk-jdbc-testing",
        "misk-hibernate",
        "misk-hibernate-testing"
      ).contains(name)) {
      testShardHibernate.dependsOn(testTask)
    } else {
      testShardNonHibernate.dependsOn(testTask)
    }
  }

  if (!path.startsWith(":samples") && !path.startsWith(":misk-embedded-sample")) {
    apply(plugin = "com.vanniktech.maven.publish")
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


  tasks.withType<DokkaTask>().configureEach {
    outputDirectory.set(file("$rootDir/docs/0.x"))
    dokkaSourceSets {
      configureEach {
        reportUndocumented.set(false)
        skipDeprecated.set(true)
        jdkVersion.set(8)
      }
    }
  }

}
