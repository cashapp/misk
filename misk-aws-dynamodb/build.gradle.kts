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
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(project(":misk-aws"))
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinReflect)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))

  testFixturesApi(Dependencies.awsDynamodb)
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.jakartaInject)
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
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
