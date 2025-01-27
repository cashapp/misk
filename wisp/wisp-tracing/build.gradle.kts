plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
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
