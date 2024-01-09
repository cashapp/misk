import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  implementation(libs.guice)
  implementation(libs.kotlinLogging)
  implementation(libs.prometheusClient)
  implementation(libs.prometheusHotspot)
  implementation(libs.prometheusHttpserver)
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
