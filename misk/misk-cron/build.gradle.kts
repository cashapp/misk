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
  api(Dependencies.javaxInject)
  api(project(":misk:misk"))
  api(project(":misk:misk-inject"))
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.wispLease)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk:misk-clustering"))
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk:misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
