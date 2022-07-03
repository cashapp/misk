plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.datasourceProxy)
  implementation(Dependencies.guice)
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.okHttp)
  api(project(":misk"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  api(project(":misk-service"))
  api(project(":misk-testing"))
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.wispConfig)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.openTracingDatadog)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}
