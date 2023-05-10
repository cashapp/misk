plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.okHttp)
  api(Dependencies.wireGrpcClient) // GrpcStatus
  api(Dependencies.wireRuntime) // AnyMessage
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okio)

  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}
