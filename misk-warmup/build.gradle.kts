plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(project(":misk-inject"))
  implementation(Dependencies.guava)
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-core"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing"))
}
