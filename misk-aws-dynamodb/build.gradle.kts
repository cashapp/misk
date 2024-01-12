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
  api(libs.awsDynamodb)
  api(libs.awsJavaSdkCore)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":misk-aws"))
  api(project(":misk-inject"))
  implementation(libs.kotlinReflect)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))

  testFixturesApi(libs.awsDynamodb)
  testFixturesApi(libs.guice)
  testFixturesApi(libs.jakartaInject)
  testFixturesApi(libs.tempestTestingInternal)
  testFixturesApi(project(":misk-aws-dynamodb"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(libs.kotlinReflect)
  testFixturesImplementation(libs.tempestTesting)
  testFixturesImplementation(libs.tempestTestingDocker)
  testFixturesImplementation(libs.tempestTestingJvm)
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
