plugins {
  kotlin("jvm")
  `java-library`
  `java-test-fixtures`
}

dependencies {
  api(Dependencies.wispFeature)
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinStdLibJdk8)

  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.wispFeature)
  testFixturesApi(Dependencies.wispFeatureTesting)
  testFixturesApi(project(":misk-feature"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.kotlinStdLibJdk8)
  testFixturesImplementation(Dependencies.moshi)
  testFixturesImplementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.wispMoshi)
  testImplementation(project(":misk-testing"))
}
