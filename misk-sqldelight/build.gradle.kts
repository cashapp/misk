import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  id("java-test-fixtures")
}

dependencies {
  api(libs.sqldelightRuntime)
  implementation(libs.loggingApi)
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
