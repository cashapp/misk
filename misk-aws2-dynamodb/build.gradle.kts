plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.aws2Dynamodb)
  api(libs.awsAuth)
  api(libs.awsSdkCore)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.kotlinLogging)
  api(project(":misk-aws"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(libs.awsCore)
  implementation(libs.awsRegions)
  implementation(libs.wispLogging)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))
}
