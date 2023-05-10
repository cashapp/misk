plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.okio)
  implementation(Dependencies.wireRuntime)
}
