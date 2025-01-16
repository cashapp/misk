import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  id("java-test-fixtures")
}

dependencies {
  api(libs.guava)
  api(project(":misk-events-core"))
  api(project(":misk-hibernate"))

  testFixturesApi(libs.jakartaInject)
  testFixturesApi(project(":misk-events-core"))
  testFixturesApi(project(":misk-inject"))

  testFixturesImplementation(libs.guice)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.okio)
  testImplementation(project(":misk-testing"))

  testImplementation(libs.guice)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
