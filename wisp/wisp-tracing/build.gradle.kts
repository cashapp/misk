plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(Dependencies.openTracingApi)

    testFixturesImplementation(Dependencies.openTracingMock)

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(Dependencies.openTracingMock)
}
