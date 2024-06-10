plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(project(":wisp:wisp-deployment"))
}
