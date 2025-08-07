import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guice)
  api(libs.jacksonAnnotations)
  api(libs.jacksonDatabind)
  api(libs.jakartaInject)
  api(libs.kotlinXHtml)
  api(libs.moshiCore)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":wisp:wisp-resource-loader"))
  api(project(":misk-inject"))
  implementation(libs.apacheCommonsLang)
  implementation(libs.guava)
  implementation(libs.jacksonCore)
  implementation(libs.jacksonDataformatYaml)
  implementation(libs.jacksonJsr310)
  implementation(libs.jacksonKotlin)
  implementation(libs.loggingApi)
  implementation(libs.okio)
  implementation(project(":misk-logging"))
  implementation(project(":misk-tailwind"))


  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.slf4jApi)
  testImplementation(libs.systemStubsJupiter)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk"))
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-testing"))
  testImplementation(libs.junitPioneer)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
// Allows us to set environment variables in tests using JUnit Pioneer
tasks.test {
  jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
  jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
