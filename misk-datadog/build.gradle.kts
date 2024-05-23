import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.kotlinLogging)
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.openTracing)
  implementation(libs.openTracingUtil)
  implementation(libs.slf4jApi)
  implementation(libs.tracingDatadog)
  implementation(project(":wisp:wisp-logging"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
