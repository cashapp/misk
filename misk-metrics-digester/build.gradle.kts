plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.okio)
  implementation(libs.wireRuntime)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.wispTimeTesting)
}
