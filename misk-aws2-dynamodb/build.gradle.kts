plugins {
  kotlin("jvm")
  `java-library`
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
}
