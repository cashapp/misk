plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitProtobuf)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.moshiKotlin)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  api(Dependencies.wispLogging)
  api(Dependencies.wispMoshi)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junit4Api)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-gcp-testing"))
  testImplementation(Dependencies.okHttpMockWebServer) {
    exclude(group = "junit")
  }
}
