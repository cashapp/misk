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
  testFixturesApi(Dependencies.wispToken)
  testFixturesApi(project(":misk:misk"))
  testFixturesApi(project(":misk:misk-hibernate"))
  testFixturesApi(project(":misk:misk-inject"))
  testFixturesApi(project(":misk:misk-jobqueue"))
  testFixturesApi(project(":misk:misk-transactional-jobqueue"))
  testFixturesImplementation(project(":misk:misk-core"))
  testFixturesImplementation(project(":misk:misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.guice)
  testImplementation(Dependencies.javaxInject)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinLogging)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.moshi)
  testImplementation(Dependencies.wispLogging)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk:misk"))
  testImplementation(project(":misk:misk-inject"))
  testImplementation(project(":misk:misk-jobqueue"))
  testImplementation(project(":misk:misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
