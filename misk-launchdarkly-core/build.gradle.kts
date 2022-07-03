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
  implementation(Dependencies.wispLaunchDarkly)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.moshiAdapters)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispMoshi)
}
