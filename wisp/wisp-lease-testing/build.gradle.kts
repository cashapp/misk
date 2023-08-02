plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-lease"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
