plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.kotlinLogging)
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.openTracingApi)
  implementation(Dependencies.openTracingUtil)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.tracingDatadog)
  implementation(Dependencies.wispLogging)
}
