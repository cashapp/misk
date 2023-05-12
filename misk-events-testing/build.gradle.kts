plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(project(":misk-events-core"))
  api(project(":misk-inject"))
  implementation(libs.guice)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.okio)
  testImplementation(project(":misk-testing"))
}
