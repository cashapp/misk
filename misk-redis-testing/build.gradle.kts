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
  api(Dependencies.jedis)
  api(project(":misk-inject"))
  api(project(":misk-redis"))
  api(project(":misk-testing"))
  implementation(Dependencies.dockerApi)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(project(":wisp:wisp-containers-testing"))
  implementation(project(":wisp:wisp-logging"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
