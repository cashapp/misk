plugins {
  kotlin("jvm")
  `java-library`
  `java-test-fixtures`
}

dependencies {
  api(Dependencies.aws2Dynamodb)
  api(Dependencies.awsAuth)
  api(Dependencies.awsSdkCore)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(project(":misk-aws"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.awsCore)
  implementation(Dependencies.awsRegions)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))

  testFixturesApi(Dependencies.aws2Dynamodb)
  testFixturesApi(Dependencies.aws2DynamodbEnhanced)
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.tempest2TestingInternal)
  testFixturesApi(project(":misk-aws2-dynamodb"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(Dependencies.tempest2Testing)
  testFixturesImplementation(Dependencies.tempest2TestingDocker)
  testFixturesImplementation(Dependencies.tempest2TestingJvm)
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  // Have to clamp until DynamoDBLocal supports later versions (dependency from tempest).
  testFixturesImplementation("org.antlr:antlr4-runtime")  {
    version {
      strictly("4.9.3")
    }
  }

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.aws2DynamodbEnhanced)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk-aws2-dynamodb"))
}
