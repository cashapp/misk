plugins {
  `java-library`
  `java-test-fixtures`
}

dependencies {
  api(project(":wisp:wisp-rate-limiting"))
  api(libs.bucket4jCore)
  api(libs.micrometerCore)


  testImplementation(libs.assertj)
  testImplementation(libs.bucket4jCore) {
    artifact {
      classifier = "tests"
    }
  }
  testImplementation(libs.junitApi)
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}
