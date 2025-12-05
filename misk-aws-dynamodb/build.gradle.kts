import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.awsDynamodb)
  api(libs.awsCore)
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

  testImplementation(libs.kotlinReflect)
  testImplementation(libs.tempestTesting)
  testImplementation(libs.tempestTestingDocker)
  testImplementation(libs.tempestTestingJvm)
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-service"))

  testFixturesImplementation(libs.kotlinReflect)
  testFixturesImplementation(project(":misk-exceptions-dynamodb"))
  testFixturesImplementation(project(":misk-service"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
