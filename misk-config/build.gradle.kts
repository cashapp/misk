plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(Dependencies.wispResourceLoader)
  api(Dependencies.wispResourceLoaderTesting)
  implementation(Dependencies.apacheCommonsLang3)
  implementation(Dependencies.jacksonDatabind)
  implementation(Dependencies.jacksonDataformatYaml)
  implementation(Dependencies.jacksonJsr310)
  implementation(Dependencies.jacksonKotlin)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-inject"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":misk-testing"))
}
