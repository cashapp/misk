plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.moshi)
  api(libs.okHttp)
  api(libs.retrofit)
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(libs.retrofitScalars)
  implementation(libs.wispMoshi)
  implementation(project(":misk"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.retrofitMock)
  testImplementation(project(":misk-testing"))
}
