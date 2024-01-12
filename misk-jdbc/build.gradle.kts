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
  api(libs.datasourceProxy)
  api(libs.dockerApi)
  api(libs.dockerCore)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.moshi)
  api(libs.openTracingApi)
  api(libs.prometheusClient)
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  implementation(libs.dockerTransport)
  implementation(libs.dockerTransportHttpClient)
  implementation(libs.hikariCp)
  implementation(libs.kotlinLogging)
  implementation(libs.okio)
  implementation(project(":misk"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))
  runtimeOnly(libs.hsqldb)
  runtimeOnly(libs.mysql)
  runtimeOnly(libs.openTracingJdbc)
  runtimeOnly(libs.postgresql)

  testFixturesApi(libs.datasourceProxy)
  testFixturesApi(libs.jakartaInject)
  testFixturesApi(libs.moshi)
  testFixturesApi(libs.okHttp)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-jdbc"))
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.hikariCp)
  testFixturesImplementation(libs.kotlinLogging)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(project(":wisp:wisp-containers-testing"))
  testFixturesImplementation(project(":wisp:wisp-deployment"))
  testFixturesImplementation(project(":wisp:wisp-logging"))
  testFixturesImplementation(project(":misk"))
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesRuntimeOnly(libs.hsqldb)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.openTracingDatadog)
  testImplementation(libs.openTracingMock)
  testImplementation(project(":wisp:wisp-containers-testing"))
  testImplementation(project(":misk-jdbc"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-jdbc")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
