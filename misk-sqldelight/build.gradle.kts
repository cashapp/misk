import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.sqldelightRuntime)

  implementation(libs.loggingApi)
  implementation(project(":misk-backoff"))
  implementation(project(":wisp:wisp-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.guice)
  testImplementation(libs.jakartaInject)
  testImplementation(libs.junitApi)
  testImplementation(libs.sqldelightJdbcDriver)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-inject"))
  testImplementation(project(":misk-jdbc"))
  testImplementation(project(":misk-sqldelight"))
  testImplementation(project(":misk-sqldelight-testing"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-config"))
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(testFixtures(project(":misk-jdbc")))

  testFixturesImplementation(libs.loggingApi)
  testFixturesImplementation(project(":misk-backoff"))
  testFixturesImplementation(project(":wisp:wisp-logging"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
