dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.awsDynamodb)
  implementation(Dependencies.tempestTestingJvm)
  implementation(Dependencies.tempestTestingDocker)
  implementation(Dependencies.tempestTestingInternal)
  implementation(project(":misk-aws-dynamodb"))
  api(project(":misk"))
  api(project(":misk-aws"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-service"))
  api(project(":misk-testing"))
  api(project(":wisp-containers-testing"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.awaitility)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
