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
  api(libs.openTracingMock)
  api(libs.servletApi)
  api(project(":wisp:wisp-time-testing"))
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-api"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-testing-api"))
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
  implementation(testFixtures(project(":misk-metrics")))

  testImplementation(libs.kotlinTest)
  testImplementation(libs.mockitoKotlin)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
