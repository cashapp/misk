plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {

  api(Dependencies.moshiCore)
  api(Dependencies.moshiKotlin)

  testImplementation(Dependencies.kotestJunitRunnerJvm)
  testImplementation(Dependencies.kotestAssertions)
  testImplementation(Dependencies.assertj)
  testRuntimeOnly(Dependencies.junitEngine)
}
