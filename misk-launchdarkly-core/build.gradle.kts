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
  api(Dependencies.wispLaunchDarkly)
  api(project(":misk-feature"))
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinStdLibJdk8)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.launchDarkly)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.moshi)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispMoshi)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
