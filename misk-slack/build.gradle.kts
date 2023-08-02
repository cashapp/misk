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
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(Dependencies.retrofit)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.kotlinReflect)
  implementation(project(":misk-actions"))
  implementation(Dependencies.retrofitMoshi)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.guava)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.okHttpMockWebServer)
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation("com.squareup.okio:okio:3.3.0")
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
