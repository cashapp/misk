import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  api(libs.gax)
  api(libs.gcpCloudCore)
  api(libs.gcpCloudStorage)
  api(libs.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  api(libs.gcpKms)
  api(libs.gcpLogging)
  api(libs.gcpSpanner)
  api(libs.googleApiServicesStorage)
  api(libs.googleAuthLibraryCredentials)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.logbackClassic)
  api(libs.moshi)
  api(libs.okio)
  api(libs.openTracingApi)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.gcpLogback)
  implementation(libs.googleApiClient)
  implementation(libs.googleAuthLibraryOauth2)
  implementation(libs.googleCloudCoreHttp)
  implementation(libs.googleHttpClient)
  implementation(libs.googleHttpClientJackson)
  implementation(libs.kotlinLogging)
  implementation(libs.logbackCore)
  implementation(libs.openTracingDatadog)
  implementation(libs.slf4jApi)
  implementation(libs.wireRuntime)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":misk"))
  implementation(project(":misk-service"))

  testFixturesApi(libs.dockerApi)
  testFixturesApi(libs.dockerCore)
  testFixturesApi(libs.dockerTransportHttpClient)
  testFixturesApi(libs.findBugs)
  testFixturesApi(libs.gcpCloudCore)
  testFixturesApi(libs.gcpCloudStorage)
  testFixturesApi(libs.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  testFixturesApi(libs.googleApiServicesStorage)
  testFixturesApi(libs.googleHttpClient)
  testFixturesApi(libs.guice)
  testFixturesApi(libs.jakartaInject)
  testFixturesApi(libs.kotlinLogging)
  testFixturesApi(project(":misk-gcp"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(libs.assertj)
  testFixturesImplementation(libs.dockerTransport)
  testFixturesImplementation(libs.gax)
  testFixturesImplementation(libs.gcpSpanner)
  testFixturesImplementation(libs.googleAuthLibraryCredentials)
  testFixturesImplementation(libs.googleHttpClientJackson)
  testFixturesImplementation(libs.junitApi)
  testFixturesImplementation(libs.kotlinRetry)
  testFixturesImplementation(libs.kotlinTest)
  testFixturesImplementation(libs.kotlinxCoroutines)
  testFixturesImplementation(libs.moshi)
  testFixturesImplementation(project(":wisp:wisp-containers-testing"))
  testFixturesImplementation(project(":wisp:wisp-moshi"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesImplementation(project(":misk-testing"))

  testImplementation(libs.assertj)
  testImplementation(libs.dockerApi)
  testImplementation(libs.dockerCore)
  testImplementation(libs.dockerTransport)
  testImplementation(libs.dockerTransportHttpClient)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.openTracingDatadog)
  testImplementation(project(":wisp:wisp-containers-testing"))
  testImplementation(project(":wisp:wisp-tracing"))
  testImplementation(project(":misk-gcp"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-gcp")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
