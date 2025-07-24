import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":misk-inject"))
  api(libs.guice6)
  api(libs.jakartaInject)
  api(libs.okio)
  api(libs.moshiCore)
  implementation(project(":wisp:wisp-moshi"))
  implementation(libs.moshiAdapters)
  implementation(libs.wireMoshiAdapter)
  implementation(libs.wireRuntime)

  testImplementation(project(":misk-testing"))
  testImplementation(libs.assertj)
  testRuntimeOnly(libs.junitEngine)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
