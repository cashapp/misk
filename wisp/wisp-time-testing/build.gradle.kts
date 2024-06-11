plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
