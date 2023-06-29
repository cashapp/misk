import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.guice)
  api(project(":misk-inject"))
  implementation(Dependencies.guava)
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-core"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
