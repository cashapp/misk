import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.dockerApi)
  api(Dependencies.dockerCore)
  api(Dependencies.javaxInject)
  api(project(":misk-inject"))
  api(project(":misk-policy"))
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.dockerTransportHttpClient)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
