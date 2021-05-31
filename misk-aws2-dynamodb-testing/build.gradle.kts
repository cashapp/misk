dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.aws2Dynamodb)
  implementation(Dependencies.aws2DynamodbEnhanced)
  implementation(Dependencies.tempest2TestingJvm)
  implementation(Dependencies.tempest2TestingDocker)
  implementation(Dependencies.tempest2TestingInternal)
  implementation(project(":misk"))
  implementation(project(":misk-aws"))
  implementation(project(":misk-aws2-dynamodb"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))
  api(project(":wisp-containers-testing"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.awaitility)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
