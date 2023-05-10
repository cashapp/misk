plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.cronUtils)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(project(":misk"))
  api(project(":misk-inject"))
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.wispLease)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-clustering"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk-testing"))
}
