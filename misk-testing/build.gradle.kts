import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.assertj)
  api(libs.dockerApi)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.jettyServletApi)
  api(libs.junitApi)
  api(libs.kotlinLogging)
  api(libs.moshiCore)
  api(libs.okHttp)
  api(libs.openTracingMock)
  api(libs.servletApi)
  api(project(":wisp:wisp-time-testing"))
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-api"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(libs.guavaTestLib)
  implementation(libs.guiceTestLib)
  implementation(libs.logbackClassic)
  implementation(libs.mockitoCore)
  implementation(libs.okio)
  implementation(libs.openTracing)
  implementation(libs.slf4jApi)
  implementation(project(":wisp:wisp-containers-testing"))
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-logging-testing"))
  implementation(project(":misk-action-scopes"))
  implementation(project(":misk-config"))
  implementation(project(":misk-service"))

  testImplementation(libs.kotlinTest)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
