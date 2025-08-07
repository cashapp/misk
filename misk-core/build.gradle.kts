import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.jakartaInject)
  api(libs.kotlinRetry)
  api(libs.okHttp)
  api(project(":misk-backoff")) // TODO remove once all usages depend on misk-backoff directly
  api(project(":misk-config"))
  api(project(":misk-logging")) // TODO remove once all usages depend on misk-logging directly
  api(project(":misk-sampling")) // TODO remove once all usages depend on misk-sampling directly
  api(project(":misk-tokens")) // TODO remove once all usages depend on misk-tokens directly
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-ssl"))
  implementation(libs.bouncyCastleProvider)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.okio)
  implementation(project(":wisp:wisp-resource-loader"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesCore)
  testImplementation(libs.logbackClassic)
  testImplementation(project(":misk-logging"))
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
