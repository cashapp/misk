plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.moshiKotlin)
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-policy"))
  implementation(Dependencies.okio)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.mockitoCore)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
