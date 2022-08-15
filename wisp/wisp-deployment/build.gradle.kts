plugins {
    `java-library`
}

dependencies {
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))
}
