plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-deployment"))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.mockitoCore)
}
