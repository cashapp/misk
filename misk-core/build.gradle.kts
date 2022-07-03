plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.wispConfig)
  api(Dependencies.wispLease)
  api(Dependencies.wispLeaseTesting)
  api(Dependencies.wispLogging)
  api(Dependencies.wispResourceLoader)
  api(Dependencies.wispResourceLoaderTesting)
  api(Dependencies.wispSsl)
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  api(Dependencies.kotlinRetry)
  api(Dependencies.loggingApi)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.okio)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.slf4jApi)

  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.wispLoggingTesting)
}
