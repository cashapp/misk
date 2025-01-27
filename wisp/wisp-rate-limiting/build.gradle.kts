plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
  id("java-test-fixtures")
}

dependencies {
  api(libs.micrometerCore)

  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}
