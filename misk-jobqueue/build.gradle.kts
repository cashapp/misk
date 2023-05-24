plugins {
  kotlin("jvm")
  `java-library`
  `java-test-fixtures`
}

dependencies {
  testFixturesApi(Dependencies.guice)
  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.wispToken)
  testFixturesApi(project(":misk"))
  testFixturesApi(project(":misk-hibernate"))
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-jobqueue"))
  testFixturesApi(project(":misk-transactional-jobqueue"))
  testFixturesImplementation(project(":misk-core"))
  testFixturesImplementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinLogging)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.moshi)
  testImplementation(Dependencies.wispLogging)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk-testing"))
}
