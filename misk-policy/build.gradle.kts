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
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.moshi)
  api(libs.okHttp)
  api(libs.retrofit)
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.prometheusClient)
  implementation(libs.retrofitScalars)
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":misk"))

  testFixturesApi(libs.dockerApi)
  testFixturesApi(libs.dockerCore)
  testFixturesApi(libs.jakartaInject)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-policy"))
  testFixturesImplementation(libs.dockerTransport)
  testFixturesImplementation(libs.dockerTransportHttpClient)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.kotlinLogging)
  testFixturesImplementation(libs.okHttp)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(project(":wisp:wisp-logging"))
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.retrofitMock)
  testImplementation(project(":misk-policy"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-metrics")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
