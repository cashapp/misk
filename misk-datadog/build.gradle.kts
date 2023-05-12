plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.kotlinLogging)
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.openTracingApi)
  implementation(libs.openTracingDatadogApi)
  implementation(libs.openTracingUtil)
  implementation(libs.slf4jApi)
  implementation(libs.wispLogging)
}
