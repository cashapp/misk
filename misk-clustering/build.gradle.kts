import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(libs.kotlinLogging)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-lease-testing"))
  api(project(":misk-inject"))
  implementation(libs.errorproneAnnotations)
  implementation(libs.guice)
  implementation(libs.kubernetesClient)
  implementation(libs.kubernetesClientApi)
  implementation(libs.okHttp)
  implementation(project(":wisp:wisp-lease"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk-core"))
  implementation(project(":misk-lease"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
