dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitScalars)
  implementation(Dependencies.moshiKotlin)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-actions"))
  api(project(":wisp-config"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.retrofitMock)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
