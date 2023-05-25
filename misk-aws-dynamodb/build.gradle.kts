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
  api(Dependencies.awsDynamodb)
  api(Dependencies.awsJavaSdkCore)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(project(":misk-aws"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))

  testFixturesApi(Dependencies.awsDynamodb)
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.tempestTestingInternal)
  testFixturesApi(project(":misk-aws-dynamodb"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(Dependencies.kotlinReflect)
  testFixturesImplementation(Dependencies.tempestTesting)
  testFixturesImplementation(Dependencies.tempestTestingDocker)
  testFixturesImplementation(Dependencies.tempestTestingJvm)
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk-aws-dynamodb"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
