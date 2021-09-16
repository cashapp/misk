dependencies {
  api(Dependencies.okHttp)
  api(project(":misk-inject"))
  api(Dependencies.wireRuntime) // AnyMessage
  api(Dependencies.wireGrpcClient) // GrpcStatus
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okio)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
