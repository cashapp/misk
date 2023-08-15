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
  api(Dependencies.gax)
  api(Dependencies.gcpCloudCore)
  api(Dependencies.gcpCloudStorage)
  api(Dependencies.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  api(Dependencies.gcpKms)
  api(Dependencies.gcpLogging)
  api(Dependencies.gcpSpanner)
  api(Dependencies.googleApiServicesStorage)
  api(Dependencies.googleAuthLibraryCredentials)
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(Dependencies.logbackClassic)
  api(Dependencies.moshi)
  api(Dependencies.okio)
  api(Dependencies.openTracingApi)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.gcpLogback)
  implementation(Dependencies.googleApiClient)
  implementation(Dependencies.googleAuthLibraryOauth2)
  implementation(Dependencies.googleCloudCoreHttp)
  implementation(Dependencies.googleHttpClient)
  implementation(Dependencies.googleHttpClientJackson)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.logbackCore)
  implementation(Dependencies.openTracingDatadog)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.wireRuntime)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":misk"))
  implementation(project(":misk-service"))

  testFixturesApi(Dependencies.dockerApi)
  testFixturesApi(Dependencies.dockerCore)
  testFixturesApi(Dependencies.dockerTransportHttpClient)
  testFixturesApi(Dependencies.findBugs)
  testFixturesApi(Dependencies.gcpCloudCore)
  testFixturesApi(Dependencies.gcpCloudStorage)
  testFixturesApi(Dependencies.gcpDatastore) {
    exclude(group = "com.google.protobuf")
    exclude(group = "com.google.api.grpc")
    exclude(group = "io.grpc")
  }
  testFixturesApi(Dependencies.googleApiServicesStorage)
  testFixturesApi(Dependencies.googleHttpClient)
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.jakartaInject)
  testFixturesApi(Dependencies.kotlinLogging)
  testFixturesApi(project(":misk-gcp"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(Dependencies.assertj)
  testFixturesImplementation(Dependencies.dockerTransport)
  testFixturesImplementation(Dependencies.gax)
  testFixturesImplementation(Dependencies.gcpSpanner)
  testFixturesImplementation(Dependencies.googleAuthLibraryCredentials)
  testFixturesImplementation(Dependencies.googleHttpClientJackson)
  testFixturesImplementation(Dependencies.junitApi)
  testFixturesImplementation(Dependencies.kotlinRetry)
  testFixturesImplementation(Dependencies.kotlinTest)
  testFixturesImplementation(Dependencies.kotlinxCoroutines)
  testFixturesImplementation(Dependencies.moshi)
  testFixturesImplementation(project(":wisp:wisp-containers-testing"))
  testFixturesImplementation(project(":wisp:wisp-moshi"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesImplementation(project(":misk-testing"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.dockerApi)
  testImplementation(Dependencies.dockerCore)
  testImplementation(Dependencies.dockerTransport)
  testImplementation(Dependencies.dockerTransportHttpClient)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.openTracingDatadog)
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
