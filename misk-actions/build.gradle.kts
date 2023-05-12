plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.okHttp)
  api(libs.wireGrpcClient) // GrpcStatus
  api(libs.wireRuntime) // AnyMessage
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.okio)

  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
}
