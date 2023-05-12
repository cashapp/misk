plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.moshi)
  api(libs.retrofit)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.kotlinLogging)
  implementation(libs.okHttp)
  implementation(libs.retrofitMoshi)
  implementation(libs.wispLogging)
  implementation(libs.wispMoshi)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.okHttpMockWebServer)
  testImplementation(libs.okio)
  testImplementation(libs.wispDeployment)
  testImplementation(project(":misk-testing"))
}
