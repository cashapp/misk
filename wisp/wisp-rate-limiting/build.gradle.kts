plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
  id("java-test-fixtures")
}

dependencies {
  api(libs.micrometerCore)

  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}
