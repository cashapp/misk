plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.wispFeature)
  api(Dependencies.wispLaunchDarkly)
  api(project(":misk-feature"))
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinStdLibJdk8)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.launchDarkly)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.moshi)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispMoshi)
}
