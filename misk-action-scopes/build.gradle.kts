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
  api(Dependencies.javaxInject)
  api(project(":misk-inject"))
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.moshi)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
