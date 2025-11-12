import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":misk-inject"))
  api(libs.opentelemetryApi)
  implementation(libs.findbugsJsr305)
  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.opentelemetrySdk)
  implementation(libs.opentelemetrySdkMetrics)
  implementation(libs.opentelemetryExporterOtlp)
  implementation(libs.opentelemetrySemconv)
  implementation(libs.opentelemetryInstrumentationRuntimeMetricsJava8)
  implementation(libs.opentelemetryInstrumentationRuntimeMetricsJava17)

  testImplementation(project(":misk-testing"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
