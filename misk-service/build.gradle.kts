import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.loggingApi)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.moshiCore)
  implementation(project(":misk-moshi"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":misk-logging"))

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
