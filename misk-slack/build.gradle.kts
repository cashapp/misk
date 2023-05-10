plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.moshi)
  api(Dependencies.retrofit)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.wispLogging)
  implementation(Dependencies.wispMoshi)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.okHttpMockWebServer)
  testImplementation("com.squareup.okio:okio:3.0.0")
  testImplementation(Dependencies.wispDeployment)
  testImplementation(project(":misk-testing"))
}
