plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.okio)
  implementation(Dependencies.wireRuntime)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.wispTimeTesting)
}
