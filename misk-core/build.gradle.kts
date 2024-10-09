import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(libs.loggingApi)
  api(libs.kotlinRetry)
  api(libs.okHttp)
  api(libs.slf4jApi)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-ssl"))
  api(project(":wisp:wisp-token"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)
  implementation(project(":wisp:wisp-resource-loader"))
  implementation(project(":wisp:wisp-token-testing"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesCore)
  testImplementation(libs.logbackClassic)
  testImplementation(project(":wisp:wisp-logging"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
