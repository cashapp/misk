import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.jakartaInject)
  api(project(":misk-inject"))
  api(project(":misk-testing-api"))
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesCore)
  testImplementation(libs.logbackClassic)
  testImplementation(project(":wisp:wisp-logging"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-testing-api"))

  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestAssertionsShared)
  testImplementation(libs.kotestCommon)
  testImplementation(libs.kotestFrameworkEngine)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.kotestJunitRunnerJvm)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
