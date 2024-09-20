import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  implementation(libs.guice)
  implementation(libs.loggingApi)
  implementation(libs.prometheusClient)
  implementation(libs.prometheusHotspot)
  implementation(libs.prometheusHttpserver)
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
