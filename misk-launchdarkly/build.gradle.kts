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
  api(Dependencies.jakartaInject)
  api(Dependencies.launchDarkly)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.micrometerCore)
  implementation(Dependencies.moshi)
  implementation(project(":misk-feature"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-feature"))
  implementation(project(":wisp:wisp-launchdarkly"))
  implementation(project(":wisp:wisp-ssl"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
