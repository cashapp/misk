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
  implementation(Dependencies.guice)
  implementation(Dependencies.sqldelightJdbcDriver)
  implementation(project(":misk-core"))
  implementation(project(":wisp:wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-jdbc"))
  testImplementation(testFixtures(project(":misk-jdbc")))
  testImplementation(project(":misk-sqldelight-testing"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
