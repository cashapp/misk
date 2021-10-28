dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  api(Dependencies.launchDarkly)
  api(Dependencies.moshiKotlin)
  implementation(project(":wisp-client"))
  implementation(project(":wisp-feature"))
  implementation(project(":wisp-logging"))
  implementation(project(":wisp-ssl"))
  api(project(":wisp-config"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.moshiAdapters)
  testImplementation(project(":wisp-moshi"))

}
