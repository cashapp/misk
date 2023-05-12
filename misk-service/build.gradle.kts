plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guice)
  api(project(":misk-inject"))
  implementation(libs.guava)
  implementation(libs.javaxInject)
  implementation(libs.kotlinLogging)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.wispLogging)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":misk-testing"))
}
