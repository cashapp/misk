import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(platform(libs.openTelemetryBom))
  api(libs.openTelemetryApi)
  api(libs.openTelemetrySdkAutoconfigure)
  api(libs.openTelemetrySdkAutoconfigureSpi)
  api(project(":misk-inject"))

  implementation(libs.guice)
  implementation(libs.loggingApi)
  implementation(libs.openTelemetryRuntime)
  implementation(libs.openTelemetrySdk)
  implementation(project(":misk-logging"))

  runtimeOnly(libs.openTelemetryExporterOtlp)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.okHttp)
  testImplementation(libs.openTelemetryExporterOtlp)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGeneratePublicationMarkdown"))
  )
}
