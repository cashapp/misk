plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.openTracing)

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.junitEngine)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(Dependencies.openTracingMock)
}
