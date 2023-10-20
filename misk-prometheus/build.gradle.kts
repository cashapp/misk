import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.guava)
  api(Dependencies.jakartaInject)
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.prometheusClient)
  implementation(Dependencies.prometheusHotspot)
  implementation(Dependencies.prometheusHttpserver)
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
