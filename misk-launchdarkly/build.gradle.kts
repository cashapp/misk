import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.launchDarkly)
  api(project(":wisp:wisp-config"))
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.jakartaInject)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.micrometerCore)
  implementation(Dependencies.moshi)
  implementation(project(":wisp:wisp-feature"))
  implementation(project(":wisp:wisp-launchdarkly"))
  implementation(project(":wisp:wisp-ssl"))
  implementation(project(":misk-feature"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
