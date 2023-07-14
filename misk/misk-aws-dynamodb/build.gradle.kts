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
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(project(":misk:misk-aws"))
  api(project(":misk:misk-core"))
  api(project(":misk:misk-inject"))
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk:misk-exceptions-dynamodb"))
  implementation(project(":misk:misk-service"))

  testFixturesApi(Dependencies.awsDynamodb)
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.tempestTestingInternal)
  testFixturesApi(project(":misk:misk-aws-dynamodb"))
  testFixturesApi(project(":misk:misk-inject"))
  testFixturesApi(project(":misk:misk-testing"))
  testFixturesImplementation(Dependencies.kotlinReflect)
  testFixturesImplementation(Dependencies.tempestTesting)
  testFixturesImplementation(Dependencies.tempestTestingDocker)
  testFixturesImplementation(Dependencies.tempestTestingJvm)
  testFixturesImplementation(project(":misk:misk-core"))
  testFixturesImplementation(project(":misk:misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk:misk-aws-dynamodb"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
