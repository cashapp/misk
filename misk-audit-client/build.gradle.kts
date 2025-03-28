import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.jakartaInject)
  api(project(":misk-inject"))
  implementation(libs.guice)

  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
  testImplementation(libs.assertj)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(testFixtures(project(":misk-metrics")))
  testImplementation(libs.okioFakefilesystem)

  testFixturesImplementation(project(":misk"))
  testFixturesImplementation(project(":misk-inject"))
  testFixturesImplementation(project(":misk-testing-api"))
  testFixturesImplementation(project(":wisp:wisp-logging"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
