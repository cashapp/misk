plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.wispFeature)
  api(libs.wispLaunchDarkly)
  api(project(":misk-feature"))
  implementation(libs.guava)
  implementation(libs.kotlinStdLibJdk8)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.launchDarkly)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.moshi)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(libs.wispMoshi)
}
