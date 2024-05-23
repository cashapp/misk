import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(project(":wisp:wisp-feature"))
  api(project(":wisp:wisp-launchdarkly"))
  api(project(":misk-feature"))
  implementation(libs.kotlinStdLibJdk8)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.launchDarkly)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.micrometerCore)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.moshiCore)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-moshi"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
