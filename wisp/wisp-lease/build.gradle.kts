plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-deployment"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.mockitoCore)
}
