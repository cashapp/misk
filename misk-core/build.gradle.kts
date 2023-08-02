import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.guava)
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(Dependencies.kotlinRetry)
  api(Dependencies.okHttp)
  api(Dependencies.slf4jApi)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-ssl"))
  api(project(":wisp:wisp-token"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(project(":wisp:wisp-resource-loader"))
  implementation(project(":wisp:wisp-token-testing"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(project(":wisp:wisp-logging"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
