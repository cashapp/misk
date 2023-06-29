import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(Dependencies.wispConfig)
  api(Dependencies.wispLeaseTesting)
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kubernetesClient)
  implementation(Dependencies.kubernetesClientApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.wispLease)
  implementation(Dependencies.wispLogging)
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
