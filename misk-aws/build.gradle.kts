dependencies {
  implementation(Dependencies.awsS3)
  implementation(Dependencies.awsSqs)
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.openTracingDatadog)
  implementation(Dependencies.prometheusClient)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-feature"))
  implementation(project(":misk-hibernate"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-jobqueue"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":misk-transactional-jobqueue"))
  api(project(":wisp-aws-environment"))
  api(project(":wisp-config"))
  api(project(":wisp-containers-testing"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.docker)
  testImplementation(Dependencies.awaitility)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-feature-testing"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
