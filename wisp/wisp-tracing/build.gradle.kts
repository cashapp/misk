plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(libs.openTracing)

    testFixturesImplementation(libs.openTracingMock)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.openTracingMock)
}
