dependencies {
  implementation(Dependencies.gcpCloudCore)
  implementation(Dependencies.gcpCloudStorage)
  implementation(Dependencies.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  implementation(Dependencies.gcpKms)
  implementation(Dependencies.gcpLogback)
  implementation(Dependencies.gcpLogging)
  implementation(Dependencies.gcpSpanner)
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.openTracingDatadog)
  implementation(Dependencies.docker)
  implementation(Dependencies.kotlinRetry)
  // The docker-java we use in tests depends on an old version of junixsocket that depends on
  // log4j. We force it up a minor version in packages that use it.
  implementation("com.kohlschutter.junixsocket:junixsocket-native-common:2.4.0") {
    isForce = true
  }
  implementation("com.kohlschutter.junixsocket:junixsocket-common:2.4.0") {
    isForce = true
  }
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.wireGrpcClient)
  implementation(Dependencies.wireRuntime)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(project(":wisp-config"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-gcp-testing"))
}
