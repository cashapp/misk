plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  api(project(":misk-testing-api"))
  api(libs.logbackClassic)
  implementation(libs.logbackCore)
  implementation(libs.slf4jApi)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":wisp:wisp-logging"))
}
