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
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(Dependencies.wispResourceLoader)
  api(project(":misk-inject"))
  implementation(Dependencies.apacheCommonsLang3)
  implementation(Dependencies.guice)
  implementation(Dependencies.jacksonCore)
  implementation(Dependencies.jacksonDataformatYaml)
  implementation(Dependencies.jacksonJsr310)
  implementation(Dependencies.jacksonKotlin)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispLogging)
  implementation(Dependencies.wispResourceLoaderTesting)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.slf4jApi)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
