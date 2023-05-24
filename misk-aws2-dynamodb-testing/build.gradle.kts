plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.aws2Dynamodb)
  api(Dependencies.aws2DynamodbEnhanced)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.tempest2TestingInternal)
  api(project(":misk-aws2-dynamodb"))
  api(project(":misk-inject"))
  api(project(":misk-testing"))
  implementation(Dependencies.tempest2Testing)
  implementation(Dependencies.tempest2TestingDocker)
  implementation(Dependencies.tempest2TestingJvm)
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
}
