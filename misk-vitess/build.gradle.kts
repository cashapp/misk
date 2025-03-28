import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {

  api(libs.dockerApi)

  testFixturesApi(libs.docker)
  testFixturesApi(libs.dockerApi)
  testFixturesApi(libs.dockerCore)
  testFixturesApi(libs.dockerTransportHttpClient)
  testFixturesApi(project(":misk-docker"))
  testFixturesApi(project(":misk-testing"))

  testFixturesImplementation(libs.mysqlConnector)
  testFixturesImplementation(libs.moshiKotlin)
  testFixturesImplementation(libs.dockerApi)
  testFixturesImplementation(libs.dockerTransportCore)
  testFixturesImplementation(libs.dockerTransportHttpClient)
  testFixturesImplementation(project(":misk-docker"))
  testFixturesImplementation(project(":wisp:wisp-moshi"))
  testFixturesImplementation(project(":wisp:wisp-resource-loader"))

  testImplementation(libs.mysqlConnector)
  testImplementation(libs.junitApi)
  testImplementation(libs.docker)
  testImplementation(libs.dockerApi)
  testImplementation(libs.dockerCore)
  testImplementation(libs.dockerTransportHttpClient)
  testImplementation(libs.dockerTransportCore)
  testImplementation(project(":misk-docker"))
  testRuntimeOnly(libs.junitEngine)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
