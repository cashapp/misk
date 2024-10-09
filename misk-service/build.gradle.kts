import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  implementation(libs.loggingApi)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.moshiCore)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))

  testImplementation(libs.assertj)
  testImplementation(libs.javaxInject)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
