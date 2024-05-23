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
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":misk-api"))
  api(project(":misk-inject"))
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.kotlinxCoroutinesCore)
  implementation(libs.moshiCore)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
