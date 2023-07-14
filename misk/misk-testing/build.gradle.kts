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
  api(Dependencies.javaxInject)
  api(Dependencies.jettyServletApi)
  api(Dependencies.junitApi)
  api(Dependencies.kotlinLogging)
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(Dependencies.openTracingMock)
  api(Dependencies.servletApi)
  api(Dependencies.wispTimeTesting)
  api(project(":misk:misk"))
  api(project(":misk:misk-actions"))
  api(project(":misk:misk-core"))
  api(project(":misk:misk-inject"))
  implementation(Dependencies.guavaTestLib)
  implementation(Dependencies.guiceTestLib)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.mockitoCore)
  implementation(Dependencies.okio)
  implementation(Dependencies.openTracingApi)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.wispContainersTesting)
  implementation(Dependencies.wispDeployment)
  implementation(Dependencies.wispLogging)
  implementation(Dependencies.wispLoggingTesting)
  implementation(project(":misk:misk-action-scopes"))
  implementation(project(":misk:misk-config"))
  implementation(project(":misk:misk-service"))

  testImplementation(Dependencies.kotlinTest)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
