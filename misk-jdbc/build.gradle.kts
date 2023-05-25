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
  api(Dependencies.datasourceProxy)
  api(Dependencies.dockerApi)
  api(Dependencies.dockerCore)
  api(Dependencies.javaxInject)
  api(Dependencies.moshi)
  api(Dependencies.openTracingApi)
  api(Dependencies.prometheusClient)
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.dockerTransportHttpClient)
  implementation(Dependencies.guice)
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispLogging)
  implementation(Dependencies.wispMoshi)
  implementation(project(":misk"))
  implementation(project(":misk-service"))
  runtimeOnly(Dependencies.hsqldb)
  runtimeOnly(Dependencies.mysql)
  runtimeOnly(Dependencies.openTracingJdbc)
  runtimeOnly(Dependencies.postgresql)

  testFixturesApi(Dependencies.datasourceProxy)
  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.moshi)
  testFixturesApi(Dependencies.okHttp)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-jdbc"))
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.hikariCp)
  testFixturesImplementation(Dependencies.kotlinLogging)
  testFixturesImplementation(Dependencies.okio)
  testFixturesImplementation(Dependencies.wispContainersTesting)
  testFixturesImplementation(Dependencies.wispDeployment)
  testFixturesImplementation(Dependencies.wispLogging)
  testFixturesImplementation(project(":misk"))
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesRuntimeOnly(Dependencies.hsqldb)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.openTracingDatadog)
  testImplementation(Dependencies.openTracingMock)
  testImplementation(Dependencies.wispContainersTesting)
  testImplementation(project(":misk-jdbc"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-jdbc")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
