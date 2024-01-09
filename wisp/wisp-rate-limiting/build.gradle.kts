plugins {
  `java-library`
  `java-test-fixtures`
}

dependencies {
  api(libs.micrometerCore)

  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}
