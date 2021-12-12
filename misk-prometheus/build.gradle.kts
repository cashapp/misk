plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.prometheusHttpserver)
  implementation(Dependencies.prometheusHotspot)
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  api(project(":wisp-config"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-gcp-testing"))
}
