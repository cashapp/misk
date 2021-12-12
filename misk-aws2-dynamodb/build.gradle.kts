plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-exceptions-dynamodb"))
  api(Dependencies.aws2Dynamodb)

  implementation(Dependencies.guice)
  implementation(project(":misk-aws"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
}
