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
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(Dependencies.moshi)
  api(Dependencies.openTracingApi)
  api(Dependencies.prometheusClient)
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.dockerTransportHttpClient)
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(project(":misk"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))
  runtimeOnly(Dependencies.hsqldb)
  runtimeOnly(Dependencies.mysql)
  runtimeOnly(Dependencies.openTracingJdbc)
  runtimeOnly(Dependencies.postgresql)

  testFixturesApi(Dependencies.datasourceProxy)
  testFixturesApi(Dependencies.jakartaInject)
  testFixturesApi(Dependencies.moshi)
  testFixturesApi(Dependencies.okHttp)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-jdbc"))
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.hikariCp)
  testFixturesImplementation(Dependencies.kotlinLogging)
  testFixturesImplementation(Dependencies.okio)
  testFixturesImplementation(project(":wisp:wisp-containers-testing"))
  testFixturesImplementation(project(":wisp:wisp-deployment"))
  testFixturesImplementation(project(":wisp:wisp-logging"))
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
