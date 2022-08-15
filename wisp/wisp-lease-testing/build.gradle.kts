plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-lease"))
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
}
