plugins {
  `java-library`
  `java-test-fixtures`
}

dependencies {
  api(project(":wisp:wisp-rate-limiting"))
  api(Dependencies.bucket4jCore)
  api(Dependencies.micrometerCore)


  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.bucket4jCore) {
    artifact {
      classifier = "tests"
    }
  }
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}
