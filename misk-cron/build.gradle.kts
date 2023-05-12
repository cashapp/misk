plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.cronUtils)
  api(libs.guice)
  api(libs.javaxInject)
  api(project(":misk"))
  api(project(":misk-inject"))
  implementation(libs.guava)
  implementation(libs.kotlinLogging)
  implementation(libs.wispLease)
  implementation(libs.wispLogging)
  implementation(project(":misk-clustering"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(libs.wispTimeTesting)
  testImplementation(project(":misk-testing"))
}
