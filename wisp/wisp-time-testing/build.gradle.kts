plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  api(project(":misk-testing-api"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}
