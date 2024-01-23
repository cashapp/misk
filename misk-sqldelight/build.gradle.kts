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
  api(libs.sqldelightRuntime)
  implementation(libs.kotlinLogging)
  implementation(project(":wisp:wisp-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.guice)
  testImplementation(libs.jakartaInject)
  testImplementation(libs.junitApi)
  testImplementation(libs.sqldelightJdbcDriver)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-config"))
  implementation(project(":misk-core"))
  testImplementation(project(":misk-inject"))
  testImplementation(project(":misk-jdbc"))
  testImplementation(testFixtures(project(":misk-jdbc")))
  testImplementation(project(":misk-sqldelight"))
  testImplementation(project(":misk-sqldelight-testing"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-config"))
  testImplementation(project(":wisp:wisp-deployment"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
