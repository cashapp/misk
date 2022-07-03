plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.aws2Dynamodb)
  implementation(Dependencies.aws2DynamodbEnhanced)

  // tempest uses old log4j
  implementation(Dependencies.tempest2TestingInternal) {
    exclude("org.apache.logging.log4j", "log4j-core")
    exclude("org.apache.logging.log4j", "log4j-api")
  }
  // tempest uses old log4j
  implementation(Dependencies.tempest2TestingJvm) {
    exclude("org.apache.logging.log4j")
  }
  implementation(Dependencies.tempest2TestingDocker)
  // for tempest...
  implementation("org.apache.logging.log4j:log4j-core:2.17.2")
  implementation("org.apache.logging.log4j:log4j-api:2.17.2")

  implementation(project(":misk"))
  implementation(project(":misk-aws"))
  implementation(project(":misk-aws2-dynamodb"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))
  api(Dependencies.wispContainersTesting)
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.awaitility)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
}
