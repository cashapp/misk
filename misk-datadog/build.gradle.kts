import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.loggingApi)
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.openTracing)
  implementation(libs.openTracingUtil)
  implementation(libs.slf4jApi)
  implementation(libs.tracingDatadog)
  implementation(project(":wisp:wisp-logging"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
