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
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.openTracingDatadog)

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

apply(from = "$rootDir/gradle-mvn-publish.gradle")
