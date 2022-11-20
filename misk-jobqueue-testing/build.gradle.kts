plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  api(project(":misk"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  api(project(":misk-testing"))
  api(project(":misk-hibernate"))
  api(project(":misk-jobqueue"))
  api(project(":misk-transactional-jobqueue"))

  testImplementation(project(":misk-hibernate"))
  testImplementation(project(":misk-hibernate-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.loggingApi)
  testImplementation(Dependencies.wispConfig)
  testImplementation(Dependencies.wispLogging)
  testImplementation(Dependencies.wispTimeTesting)
}
