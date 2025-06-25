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
  implementation(project(":misk-tailwind"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-resource-loader-testing"))

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
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
