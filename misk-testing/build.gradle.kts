import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.assertj)
  api(Dependencies.dockerApi)
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(Dependencies.junitApi)
  api(Dependencies.kotlinLogging)
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(Dependencies.openTracingMock)
  api(project(":wisp:wisp-time-testing"))
  api(project(":misk"))
  api(project(":misk-api"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.guavaTestLib)
  implementation(Dependencies.guiceTestLib)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.mockitoCore)
  implementation(Dependencies.okio)
  implementation(Dependencies.openTracingApi)
  implementation(Dependencies.slf4jApi)
  implementation(project(":wisp:wisp-containers-testing"))
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-logging-testing"))
  implementation(project(":misk-action-scopes"))
  implementation(project(":misk-config"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":misk-actions"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
