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
  api(Dependencies.jakartaInject)
  api(project(":wisp:wisp-feature"))
  api(project(":wisp:wisp-launchdarkly"))
  api(project(":misk-feature"))
  implementation(Dependencies.kotlinStdLibJdk8)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.launchDarkly)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.micrometerCore)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.moshi)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-moshi"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
