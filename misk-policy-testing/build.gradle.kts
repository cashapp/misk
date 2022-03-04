plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.okio)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.okHttp)
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  api(project(":misk-policy"))
  api(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.mockitoCore)
}
