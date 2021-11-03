sourceSets {
  val main by getting {
    java.srcDir("src/main/kotlin/")
  }
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.gcpCloudCore)
  implementation(Dependencies.gcpCloudStorage)
  implementation(Dependencies.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  implementation(Dependencies.gcpSpanner)
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
  implementation(Dependencies.assertj)
  implementation(Dependencies.junitApi)
  implementation(Dependencies.kotlinTest)
  api(project(":misk"))
  api(project(":misk-core"))
  api(project(":misk-gcp"))
  api(project(":misk-inject"))
  api(project(":misk-service"))
  api(project(":misk-testing"))
  api(project(":wisp-moshi"))
}
