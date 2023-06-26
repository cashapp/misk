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
  api(Dependencies.wispFeature)
  api(Dependencies.wispFeatureTesting)
  api(project(":misk-feature"))
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.moshi)
  implementation(project(":misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
