plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api("com.amazonaws:aws-java-sdk-core:1.11.960")
  api(Dependencies.kotlinLogging)
  api(Dependencies.javaxInject)
  api(Dependencies.awsDynamodb)
  api(Dependencies.guice)
  api(project(":misk-aws"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.wispLogging)
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.0")
  implementation(project(":misk-exceptions-dynamodb"))
  implementation(project(":misk-service"))
}
