dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.docker)
  implementation(Dependencies.okio)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.okHttp)
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  api(project(":misk-policy"))
  api(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.mockitoCore)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
