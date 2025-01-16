import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
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

  if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
    // Without this, we can't compile on Apple Silicon currently.
    // This is likely not necessary to have long term,
    // so we should remove it when things get fixed upstream.
    testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
  }

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
