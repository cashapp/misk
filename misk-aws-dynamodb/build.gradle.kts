dependencies {
  api(project(":misk-exceptions-dynamodb"))

  implementation(Dependencies.guice)
  implementation(Dependencies.awsDynamodb)
  implementation(project(":misk-aws"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
}
