import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.aws2Dynamodb)
  api(libs.aws2DynamodbEnhanced)
  api(libs.aws2Auth)
  api(libs.awsSdkCore)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":misk-aws"))
  api(project(":misk-inject"))
  implementation(libs.aws2Core)
  implementation(libs.aws2Regions)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))

  testFixturesApi(libs.aws2Dynamodb)
  testFixturesApi(libs.aws2DynamodbEnhanced)
  testFixturesApi(libs.guice)
  testFixturesApi(libs.jakartaInject)
  testFixturesApi(libs.tempest2TestingInternal)
  testFixturesApi(project(":misk-aws2-dynamodb"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(libs.tempest2Testing)
  testFixturesImplementation(libs.tempest2TestingDocker)
  testFixturesImplementation(libs.tempest2TestingJvm)
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.aws2DynamodbEnhanced)
  testImplementation(libs.junitApi)
  // Have to clamp until DynamoDBLocal supports later versions (dependency from tempest).
  testRuntimeOnly("org.antlr:antlr4-runtime") {
    version {
      strictly("4.9.3")
    }
  }

  if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
    // Without this, we can't compile on Apple Silicon currently.
    // This is likely not necessary to have long term,
    // so we should remove it when things get fixed upstream.
    testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
  }

  testImplementation(libs.tempest2Testing)
  testImplementation(libs.tempest2TestingDocker)
  testImplementation(libs.tempest2TestingJvm)
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-service"))

  testFixturesImplementation(libs.aws2Core)
  testFixturesImplementation(libs.aws2Regions)
  testFixturesImplementation(project(":misk-exceptions-dynamodb"))
  testFixturesImplementation(project(":misk-service"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
