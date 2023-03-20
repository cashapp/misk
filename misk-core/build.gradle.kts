plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.kotlinRetry)
  api(Dependencies.loggingApi)
  api(Dependencies.wispConfig)
  api(Dependencies.wispLease)
  api(Dependencies.wispLeaseTesting)
  api(Dependencies.wispLogging)
  api(Dependencies.wispResourceLoader)
  api(Dependencies.wispResourceLoaderTesting)
  api(Dependencies.wispSsl)
  api(Dependencies.wispToken)
  api(Dependencies.wispTokenTesting)
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.slf4jApi)
  api(project(":misk-config"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.wispLoggingTesting)
}
