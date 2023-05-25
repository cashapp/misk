import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.kotlinLogging)
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.openTracingApi)
  implementation(Dependencies.openTracingUtil)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.tracingDatadog)
  implementation(Dependencies.wispLogging)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
