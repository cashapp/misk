plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(Dependencies.retrofit)
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.retrofitScalars)
  implementation(Dependencies.wispMoshi)
  implementation(project(":misk"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.retrofitMock)
  testImplementation(project(":misk-testing"))
}
