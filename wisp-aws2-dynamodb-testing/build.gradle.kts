dependencies {
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.aws2Dynamodb)
  implementation(Dependencies.aws2DynamodbEnhanced)
  implementation(Dependencies.awsDynamodbLocal)
  api(project(":wisp-containers-testing"))
  api(project(":wisp-logging"))
  implementation(Dependencies.docker)
  // The docker-java we use in tests depends on an old version of junixsocket that depends on
  // log4j. We force it up a minor version in packages that use it.
  implementation("com.kohlschutter.junixsocket:junixsocket-native-common:2.3.3") {
    isForce = true
  }
  implementation("com.kohlschutter.junixsocket:junixsocket-common:2.3.3") {
    isForce = true
  }

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
