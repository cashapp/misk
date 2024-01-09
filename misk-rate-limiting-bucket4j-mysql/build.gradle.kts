import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  api(project(":misk-inject"))
  api(project(":wisp:wisp-rate-limiting"))
  api(project(":wisp:wisp-rate-limiting:bucket4j"))
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.micrometerCore)

  implementation(project(":misk-jdbc"))
  implementation(project(":wisp:wisp-logging"))
  implementation(libs.bucket4jCore)
  implementation(libs.bucket4jMySQL)
  implementation(libs.kotlinLogging)

  testImplementation(project(":misk"))
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-rate-limiting-bucket4j-mysql"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-config"))
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(testFixtures(project(":misk-jdbc")))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
