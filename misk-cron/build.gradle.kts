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
  implementation(libs.moshiCore)
  implementation(project(":wisp:wisp-lease"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":misk-backoff"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-config"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.logbackClassic)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
