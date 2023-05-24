plugins {
  kotlin("jvm")
  `java-library`
}

sourceSets {
  val main by getting {
    java.srcDir("src/main/kotlin/")
  }
}

dependencies {
  api(Dependencies.dockerApi)
  api(Dependencies.dockerCore)
  api(Dependencies.dockerTransportHttpClient)
  api(Dependencies.findBugs)
  api(Dependencies.gcpCloudCore)
  api(Dependencies.gcpCloudStorage)
  api(Dependencies.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  api(Dependencies.googleApiServicesStorage)
  api(Dependencies.googleHttpClient)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(project(":misk-gcp"))
  api(project(":misk-inject"))
  implementation(Dependencies.assertj)
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.gax)
  implementation(Dependencies.gcpSpanner)
  implementation(Dependencies.googleAuthLibraryCredentials)
  implementation(Dependencies.googleHttpClientJackson)
  implementation(Dependencies.junitApi)
  implementation(Dependencies.kotlinRetry)
  implementation(Dependencies.kotlinTest)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.moshi)
  implementation(Dependencies.wispContainersTesting)
  implementation(Dependencies.wispMoshi)
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))
}
