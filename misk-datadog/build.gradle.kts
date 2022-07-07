plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.tracingDatadog)
  implementation(Dependencies.openTracingDatadog)
  implementation(project(":misk-inject"))
  api(Dependencies.wispLogging)
}
