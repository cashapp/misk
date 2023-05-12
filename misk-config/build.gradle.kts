plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.jacksonAnotations)
  api(libs.jacksonDatabind)
  api(libs.javaxInject)
  api(libs.wispConfig)
  api(libs.wispDeployment)
  api(libs.wispResourceLoader)
  api(project(":misk-inject"))
  implementation(libs.apacheCommonsLang3)
  implementation(libs.guice)
  implementation(libs.jacksonCore)
  implementation(libs.jacksonDataformatYaml)
  implementation(libs.jacksonJsr310)
  implementation(libs.jacksonKotlin)
  implementation(libs.kotlinLogging)
  implementation(libs.okio)
  implementation(libs.wispLogging)
  implementation(libs.wispResourceLoaderTesting)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.slf4jApi)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-testing"))
}
