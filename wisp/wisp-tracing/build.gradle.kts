plugins {
    `java-library`
}

dependencies {
    api(libs.openTracing)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.openTracingMock)
    testImplementation(project(":wisp-tracing-testing"))
}
