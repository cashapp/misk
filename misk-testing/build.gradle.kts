import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
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
  api(libs.loggingApi)
  api(libs.moshiCore)
  api(libs.okHttp)
  api(libs.okHttpMockWebServer3)
  api(libs.openTracingMock)
  api(libs.servletApi)
  api(libs.logbackClassic)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-api"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-sampling"))
  api(project(":misk-testing-api"))
  implementation(libs.dockerCore)
  implementation(libs.dockerTransportHttpClient)
  implementation(libs.dockerTransportCore)
  implementation(libs.guavaTestLib)
  implementation(libs.guiceTestLib)
  implementation(libs.classGraph)
  implementation(libs.mockitoCore)
  implementation(libs.mockk)
  implementation(libs.okio)
  implementation(libs.openTracing)
  implementation(libs.openTracingUtil)
  implementation(libs.slf4jApi)
  implementation(libs.logbackCore)
  implementation(project(":misk-action-scopes"))
  implementation(project(":misk-config"))
  implementation(project(":misk-docker"))
  implementation(project(":misk-logging"))
  implementation(project(":misk-service"))
  implementation(project(":misk-tokens"))
  implementation(project(":wisp:wisp-deployment"))
  implementation(testFixtures(project(":misk-metrics")))

  testImplementation(libs.kotlinTest)
  testImplementation(libs.mockitoKotlin)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
