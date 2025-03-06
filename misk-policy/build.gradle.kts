import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.moshiCore)
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
  testFixturesApi(project(":misk-testing-api"))
  testFixturesImplementation(libs.dockerTransportHttpClient)
  testFixturesImplementation(libs.dockerTransportCore)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.loggingApi)
  testFixturesImplementation(libs.okHttp)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(project(":wisp:wisp-logging"))
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.retrofitMock)
  testImplementation(project(":misk-policy"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-metrics")))

  testImplementation(libs.dockerTransportHttpClient)
  testImplementation(libs.dockerTransportCore)
  testImplementation(libs.guice)
  testImplementation(libs.loggingApi)
  testImplementation(libs.okHttp)
  testImplementation(libs.okio)
  testImplementation(project(":wisp:wisp-logging"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-service"))

  testFixturesImplementation(libs.prometheusClient)
  testFixturesImplementation(libs.retrofitScalars)
  testFixturesImplementation(project(":wisp:wisp-moshi"))
  testFixturesImplementation(project(":misk"))
  testFixturesImplementation(project(":misk-docker"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
