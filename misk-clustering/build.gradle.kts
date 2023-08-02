import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.guava)
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-lease-testing"))
  api(project(":misk-inject"))
  implementation(Dependencies.errorproneAnnotations)
  implementation(Dependencies.guice)
  implementation(Dependencies.kubernetesClient)
  implementation(Dependencies.kubernetesClientApi)
  implementation(Dependencies.okHttp)
  implementation(project(":wisp:wisp-lease"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk-core"))
  implementation(project(":misk-lease"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
