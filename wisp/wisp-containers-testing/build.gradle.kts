plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)
  api(Dependencies.loggingApi)
  api(Dependencies.logbackClassic)
  api(project(":wisp-logging"))
}
