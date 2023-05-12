plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.wispToken)
  api(project(":misk"))
  api(project(":misk-hibernate"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  api(project(":misk-transactional-jobqueue"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinLogging)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.moshi)
  testImplementation(libs.wispLogging)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(libs.wispTimeTesting)
  testImplementation(project(":misk-testing"))
}
