import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(platform(libs.openTelemetryBom))
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.jettyIo)
  api(libs.jettyServer)
  api(libs.openTelemetryApi)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-inject"))

  implementation(libs.jettyJmx)
  implementation(libs.jettyUds)
  implementation(libs.jettyUnixSocket)
  implementation(libs.jettyUtil)
  implementation(libs.openTelemetryInstrumentationApi)
  implementation(libs.openTelemetryJmx)
  implementation(project(":misk-opentelemetry"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.jettyAlpnServer)
  testImplementation(libs.jettyAlpnServerJava)
  testImplementation(libs.jettyHttp2)
  testImplementation(libs.okHttp)
  testImplementation(libs.okHttpTls)
  testImplementation(libs.jettyServlet)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.openTelemetrySdk)
  testImplementation(libs.openTelemetrySdkTesting)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGeneratePublicationMarkdown"))
  )
}
