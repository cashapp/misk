import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.jacksonAnotations)
  api(Dependencies.jacksonDatabind)
  api(Dependencies.javaxInject)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":wisp:wisp-resource-loader"))
  api(project(":misk-inject"))
  implementation(Dependencies.apacheCommonsLang3)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jacksonCore)
  implementation(Dependencies.jacksonDataformatYaml)
  implementation(Dependencies.jacksonJsr310)
  implementation(Dependencies.jacksonKotlin)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-resource-loader-testing"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.slf4jApi)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk"))
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
