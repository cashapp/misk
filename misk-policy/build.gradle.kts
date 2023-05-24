plugins {
  kotlin("jvm")
  `java-library`
  `java-test-fixtures`
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

  testFixturesApi(Dependencies.dockerApi)
  testFixturesApi(Dependencies.dockerCore)
  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-policy"))
  testFixturesImplementation(Dependencies.dockerTransport)
  testFixturesImplementation(Dependencies.dockerTransportHttpClient)
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.kotlinLogging)
  testFixturesImplementation(Dependencies.okHttp)
  testFixturesImplementation(Dependencies.okio)
  testFixturesImplementation(Dependencies.wispLogging)
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.retrofitMock)
  testImplementation(project(":misk-testing"))
}
