dependencies {
  api(Dependencies.aws2Dynamodb)

  implementation(Dependencies.guice)
  implementation(project(":misk-aws"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
