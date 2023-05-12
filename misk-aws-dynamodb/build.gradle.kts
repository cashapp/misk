plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.awsDynamodb)
  api(libs.awsJavaSdkCore)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.kotlinLogging)
  api(project(":misk-aws"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(libs.kotlinReflect)
  implementation(libs.wispLogging)
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))
}
