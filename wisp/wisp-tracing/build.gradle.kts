plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
  id("java-test-fixtures")
}

dependencies {
    api(libs.openTracing)

    testFixturesImplementation(libs.openTracingMock)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.openTracingMock)

    testImplementation(libs.openTracingMock)
}
