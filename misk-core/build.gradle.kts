plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinLogging)
  api(Dependencies.kotlinRetry)
  api(Dependencies.okHttp)
  api(Dependencies.slf4jApi)
  api(Dependencies.wispConfig)
  api(Dependencies.wispSsl)
  api(Dependencies.wispToken)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.wispResourceLoader)
  implementation(Dependencies.wispTokenTesting)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLogging)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk-testing"))
}
