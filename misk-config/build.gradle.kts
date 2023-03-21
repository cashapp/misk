plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.apacheCommonsLang3)
  implementation(Dependencies.jacksonDatabind)
  implementation(Dependencies.jacksonDataformatYaml)
  implementation(Dependencies.jacksonKotlin)
  implementation(Dependencies.jacksonJsr310)
  implementation(Dependencies.okio)
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(Dependencies.wispResourceLoader)
  api(Dependencies.wispResourceLoaderTesting)
  implementation(project(":misk-inject"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":misk-testing"))
}
