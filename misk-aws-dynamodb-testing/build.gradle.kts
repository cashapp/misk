plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.awsDynamodb)

  // tempest uses old log4j
  implementation(Dependencies.tempestTestingInternal) {
    exclude("org.apache.logging.log4j", "log4j-core")
    exclude("org.apache.logging.log4j", "log4j-api")
  }
  // tempest uses old log4j
  implementation(Dependencies.tempestTestingJvm) {
    exclude("org.apache.logging.log4j")
  }
  implementation(Dependencies.tempestTestingDocker)
  // for tempest...
  implementation("org.apache.logging.log4j:log4j-core:2.18.0")
  implementation("org.apache.logging.log4j:log4j-api:2.18.0")

  // There is in incompatibility between com.amazonaws:DynamoDBLocal and recent ANTLR versions.
  // We have to force down to a known-working version.
  implementation("org.antlr:antlr4-runtime") { version { strictly("4.9.3") }}

  implementation(project(":misk-aws-dynamodb"))
  api(project(":misk"))
  api(project(":misk-aws"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-service"))
  api(project(":misk-testing"))
  api(Dependencies.wispContainersTesting)
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.awaitility)
}
