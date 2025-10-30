import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.kotlinxCoroutinesCore)

  testFixturesApi(libs.guice)
  testFixturesApi(libs.jakartaInject)
  testFixturesApi(project(":misk"))
  testFixturesApi(project(":misk-hibernate"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-jobqueue"))
  testFixturesApi(project(":misk-transactional-jobqueue"))
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesImplementation(project(":misk-testing-api"))

  testImplementation(libs.assertj)
  testImplementation(libs.guice)
  testImplementation(libs.jakartaInject)
  testImplementation(libs.junitApi)
  testImplementation(libs.loggingApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.moshiCore)
  testImplementation(project(":misk-logging"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk"))
  testImplementation(project(":misk-inject"))
  testImplementation(project(":misk-jobqueue"))

  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing-api"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
