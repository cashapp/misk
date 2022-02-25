plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.launchDarkly)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.moshiCore)
  implementation(project(":misk-feature"))
  implementation(project(":wisp-launchdarkly"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.moshiAdapters)
  testImplementation(project(":wisp-logging-testing"))
  testImplementation(project(":wisp-moshi"))
}
