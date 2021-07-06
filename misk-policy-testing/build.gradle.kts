dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.docker)
  implementation(Dependencies.okio)
  implementation(Dependencies.loggingApi)
  implementation(project(":misk-inject"))
  api(project(":misk-policy"))
  api(project(":misk-service"))

  testImplementation(Dependencies.assertj)
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
