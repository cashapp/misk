plugins {
  kotlin("jvm")
  id("java-gradle-plugin")
}

dependencies {
  implementation(gradleApi())
  implementation(Dependencies.docker)
  implementation(Dependencies.okio)
}

gradlePlugin {
  plugins {
    create("opa-gradle-plugin") {
      id = "misk.opa"
      implementationClass = "misk.policy.opa.OpaTestPlugin"
    }
  }
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
