import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(libs.loggingApi)
  api(libs.kotlinRetry)
  api(libs.okHttp)
  api(libs.slf4jApi)
  api(project(":misk-backoff")) // TODO remove once all usages depend on misk-backoff directly
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-ssl"))
  api(project(":wisp:wisp-token"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-testing-api"))
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)
  implementation(project(":wisp:wisp-token-testing"))
  implementation(project(":wisp:wisp-resource-loader"))

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
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
