plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {

  testImplementation(Dependencies.kotestJunitRunnerJvm)
  testImplementation(Dependencies.kotestAssertions)
  testImplementation(Dependencies.assertj)
  testRuntimeOnly(Dependencies.junitEngine)
}
