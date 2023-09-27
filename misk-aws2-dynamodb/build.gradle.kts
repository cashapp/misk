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
  api(Dependencies.aws2Dynamodb)
  api(Dependencies.awsAuth)
  api(Dependencies.awsSdkCore)
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(project(":misk-aws"))
  api(project(":misk-inject"))
  implementation(Dependencies.awsCore)
  implementation(Dependencies.awsRegions)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))

  testFixturesApi(Dependencies.aws2Dynamodb)
  testFixturesApi(Dependencies.aws2DynamodbEnhanced)
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.jakartaInject)
  testFixturesApi(Dependencies.tempest2TestingInternal)
  testFixturesApi(project(":misk-aws2-dynamodb"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(Dependencies.tempest2Testing)
  testFixturesImplementation(Dependencies.tempest2TestingDocker)
  testFixturesImplementation(Dependencies.tempest2TestingJvm)
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.aws2DynamodbEnhanced)
  testImplementation(Dependencies.junitApi)
  // Have to clamp until DynamoDBLocal supports later versions (dependency from tempest).
  testRuntimeOnly("org.antlr:antlr4-runtime") {
    version {
      strictly("4.9.3")
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
