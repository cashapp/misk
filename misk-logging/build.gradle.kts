import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.loggingApi)
  api(libs.slf4jApi)
  api(project(":misk-sampling")) // TODO remove once all usages depend on misk-sampling directly
  implementation(libs.kotlinStdLibJdk8)
  implementation(project(":misk-api"))

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
  testImplementation(libs.kotestFrameworkApi)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.kotestJunitRunnerJvm)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
