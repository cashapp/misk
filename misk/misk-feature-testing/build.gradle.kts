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
  api(Dependencies.wispFeature)
  api(Dependencies.wispFeatureTesting)
  api(project(":misk:misk-feature"))
  api(project(":misk:misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.moshi)
  implementation(project(":misk:misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
