import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.datasourceProxy)
  api(Dependencies.guava)
  api(Dependencies.javaxInject)
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(project(":misk:misk-inject"))
  api(project(":misk:misk-jdbc"))
  implementation(Dependencies.guice)
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispContainersTesting)
  implementation(Dependencies.wispDeployment)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk:misk"))
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-service"))
  runtimeOnly(Dependencies.hsqldb)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
