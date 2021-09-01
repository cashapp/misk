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
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
