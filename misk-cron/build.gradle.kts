import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.cronUtils)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":misk"))
  api(project(":misk-inject"))

  implementation(libs.loggingApi)
  implementation(libs.kotlinXHtml)
  implementation(libs.okHttp)
  implementation(libs.moshiCore)
  implementation(project(":wisp:wisp-lease"))
  implementation(project(":misk-logging"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":misk-api"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-backoff"))
  implementation(project(":misk-core"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-config"))
  implementation(project(":misk-service"))
  implementation(project(":misk-moshi"))
  implementation(project(":misk-tailwind"))
  implementation(project(":wisp:wisp-deployment"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.logbackClassic)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
