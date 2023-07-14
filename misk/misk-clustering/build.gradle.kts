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
  api(Dependencies.wispConfig)
  api(Dependencies.wispLeaseTesting)
  api(project(":misk:misk-inject"))
  implementation(Dependencies.errorproneAnnotations)
  implementation(Dependencies.guice)
  implementation(Dependencies.kubernetesClient)
  implementation(Dependencies.kubernetesClientApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.wispLease)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-lease"))
  implementation(project(":misk:misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk:misk-clustering"))
  testImplementation(project(":misk:misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
