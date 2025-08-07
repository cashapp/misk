import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.moshiCore)
  api(libs.okHttp)
  api(libs.retrofit)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.loggingApi)
  implementation(libs.kotlinReflect)
  implementation(project(":misk-actions"))
  implementation(libs.retrofitMoshi)
  implementation(project(":misk-logging"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(libs.okio)

  testImplementation(libs.assertj)
  testImplementation(libs.guava)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.moshiKotlin)
  testImplementation(libs.okHttpMockWebServer)
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation("com.squareup.okio:okio:3.3.0")
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
