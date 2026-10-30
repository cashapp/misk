import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.loggingApi)
  api(libs.openTelemetrySdkAutoconfigureSpi)
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.okHttp)
  implementation(libs.openTracing)
  implementation(libs.openTracingUtil)
  implementation(libs.slf4jApi)
  implementation(libs.tracingDatadog)
  implementation(project(":misk-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGeneratePublicationMarkdown"))
  )
}
