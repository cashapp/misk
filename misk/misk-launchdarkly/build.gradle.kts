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
  api(Dependencies.wispConfig)
  api(project(":misk:misk"))
  api(project(":misk:misk-config"))
  api(project(":misk:misk-core"))
  api(project(":misk:misk-inject"))
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.micrometerCore)
  implementation(Dependencies.moshi)
  implementation(Dependencies.wispFeature)
  implementation(Dependencies.wispLaunchDarkly)
  implementation(Dependencies.wispSsl)
  implementation(project(":misk:misk-feature"))
  implementation(project(":misk:misk-launchdarkly-core"))
  implementation(project(":misk:misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
