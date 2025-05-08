import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
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
  implementation(libs.loggingApi)
  implementation(libs.micrometerCore)
  implementation(libs.moshiCore)
  implementation(project(":misk-core"))
  implementation(project(":misk-feature"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-feature"))
  implementation(project(":wisp:wisp-launchdarkly"))
  implementation(project(":wisp:wisp-ssl"))
  implementation(project(":wisp:wisp-logging"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
