import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.loggingApi)
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.openTracing)
  implementation(libs.openTracingUtil)
  implementation(libs.slf4jApi)
  implementation(libs.tracingDatadog)
  implementation(project(":misk-logging"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
