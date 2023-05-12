plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guice)
  api(project(":misk-inject"))
  implementation(libs.guava)
  implementation(libs.javaxInject)
  implementation(libs.kotlinLogging)
  implementation(libs.wispLogging)
  implementation(project(":misk-core"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing"))
}
