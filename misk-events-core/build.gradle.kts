plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.okio)
  implementation(libs.wireRuntime)
}
