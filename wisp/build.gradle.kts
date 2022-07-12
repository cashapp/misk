import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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
    classpath(Dependencies.kotlinBinaryCompatibilityPlugin)
  }
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
    val compileKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
      }
    }
    val compileTestKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
      }
    }

    dependencies {
      add("testImplementation", Dependencies.junitApi)
      add("testRuntimeOnly", Dependencies.junitEngine)

      // Platform/BOM dependencies constrain versions only.
      // Enforce wisp-bom -- it should take priority over external BOMs.
      add("api", enforcedPlatform(project(":wisp-bom")))
      add("api", platform(Dependencies.grpcBom))
      //add("api", platform(Dependencies.guava))
      add("api", platform(Dependencies.jacksonBom))
      add("api", platform(Dependencies.kotlinBom))
      add("api", platform(Dependencies.nettyBom))
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

}

