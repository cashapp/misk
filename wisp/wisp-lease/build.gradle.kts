plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-deployment"))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.mockitoCore)
}
