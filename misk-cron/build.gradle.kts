import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.cronUtils)
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(project(":misk"))
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinLogging)
  implementation(project(":wisp:wisp-lease"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
