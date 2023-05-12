plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guice)
  api(libs.javaxInject)
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinStdLibJdk8)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}
