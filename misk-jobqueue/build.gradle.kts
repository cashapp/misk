import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(project(":wisp:wisp-token"))
  testFixturesApi(project(":misk"))
  testFixturesApi(project(":misk-hibernate"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-jobqueue"))
  testFixturesApi(project(":misk-transactional-jobqueue"))
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.guice)
  testImplementation(Dependencies.javaxInject)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinLogging)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.moshi)
  testImplementation(project(":wisp:wisp-logging"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":misk"))
  testImplementation(project(":misk-inject"))
  testImplementation(project(":misk-jobqueue"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
