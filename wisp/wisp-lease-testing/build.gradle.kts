plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(project(":wisp:wisp-lease"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
