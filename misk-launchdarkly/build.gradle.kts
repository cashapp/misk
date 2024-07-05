import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.launchDarkly)
  implementation(libs.micrometerCore)
  implementation(libs.moshiCore)
  implementation(project(":misk-core"))
  implementation(project(":misk-feature"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-feature"))
  implementation(project(":wisp:wisp-launchdarkly"))
  implementation(project(":wisp:wisp-ssl"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
