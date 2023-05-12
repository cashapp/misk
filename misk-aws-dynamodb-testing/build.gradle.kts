plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.awsDynamodb)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.tempestTestingInternal)
  api(project(":misk-aws-dynamodb"))
  api(project(":misk-inject"))
  api(project(":misk-testing"))
  implementation(libs.kotlinReflect)
  implementation(libs.tempestTesting)
  implementation(libs.tempestTestingDocker)
  implementation(libs.tempestTestingJvm)
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}
