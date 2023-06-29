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
  api(Dependencies.prometheusClient)
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
