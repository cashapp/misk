import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
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
  api(project(":wisp:wisp-lease"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk-core"))
  implementation(project(":misk-lease"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
