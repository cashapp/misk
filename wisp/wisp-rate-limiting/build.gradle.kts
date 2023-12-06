plugins {
  `java-library`
  `java-test-fixtures`
}

dependencies {
  api(Dependencies.micrometerCore)

  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}
