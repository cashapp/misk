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
  api(libs.dockerApi)
  api(libs.dockerCore)
  api(libs.dockerTransportHttpClient)
  api(libs.findBugs)
  api(libs.gcpCloudCore)
  api(libs.gcpCloudStorage)
  api(libs.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  api(libs.googleApiServicesStorage)
  api(libs.googleHttpClient)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.kotlinLogging)
  api(project(":misk-gcp"))
  api(project(":misk-inject"))
  implementation(libs.assertj)
  implementation(libs.dockerTransport)
  implementation(libs.gax)
  implementation(libs.gcpSpanner)
  implementation(libs.googleAuthLibraryCredentials)
  implementation(libs.googleHttpClientJackson)
  implementation(libs.junitApi)
  implementation(libs.kotlinRetry)
  implementation(libs.kotlinTest)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.moshi)
  implementation(libs.wispContainersTesting)
  implementation(libs.wispMoshi)
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))

  testImplementation(libs.wispDeployment)
  testImplementation(project(":misk"))
}
