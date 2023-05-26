plugins {
    `java-library`
}

dependencies {
    api(libs.openTracing)
    implementation(libs.openTracingMock)
}
