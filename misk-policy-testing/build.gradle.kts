plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.dockerApi)
  api(libs.dockerCore)
  api(libs.javaxInject)
  api(project(":misk-inject"))
  api(project(":misk-policy"))
  implementation(libs.dockerTransport)
  implementation(libs.dockerTransportHttpClient)
  implementation(libs.guice)
  implementation(libs.kotlinLogging)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.wispLogging)
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}
