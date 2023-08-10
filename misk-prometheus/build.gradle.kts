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
  api(Dependencies.prometheusClient)
  api(project(":wisp:wisp-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.prometheusHotspot)
  implementation(Dependencies.prometheusHttpserver)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
