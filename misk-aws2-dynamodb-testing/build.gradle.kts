plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.aws2Dynamodb)
  api(libs.aws2DynamodbEnhanced)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.tempest2TestingInternal)
  api(project(":misk-aws2-dynamodb"))
  api(project(":misk-inject"))
  api(project(":misk-testing"))
  implementation(libs.tempest2Testing)
  implementation(libs.tempest2TestingDocker)
  implementation(libs.tempest2TestingJvm)
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.awsSdkCore)
  testImplementation(libs.junitApi)

  // Have to clamp until DynamoDBLocal supports later versions (dependency from tempest).
  testImplementation("org.antlr:antlr4-runtime")  {
    version {
      strictly("4.9.3")
    }
  }
}
