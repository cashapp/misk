plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitScalars)
  implementation(Dependencies.moshiKotlin)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-actions"))
  api(Dependencies.wispConfig)
  api(Dependencies.wispLogging)
  api(Dependencies.wispMoshi)

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.retrofitMock)
}
