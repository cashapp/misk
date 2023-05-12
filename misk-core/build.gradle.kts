plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.kotlinLogging)
  api(libs.kotlinRetry)
  api(libs.okHttp)
  api(libs.slf4jApi)
  api(libs.wispConfig)
  api(libs.wispSsl)
  api(libs.wispToken)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.wispResourceLoader)
  implementation(libs.wispTokenTesting)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutines)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.wispLogging)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(project(":misk-testing"))
}
