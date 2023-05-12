plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.wispFeature)
  api(libs.wispFeatureTesting)
  api(project(":misk-feature"))
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.moshi)
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.wispMoshi)
  testImplementation(project(":misk-testing"))
}
