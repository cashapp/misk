plugins {
    `java-library`
}

dependencies {
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))
}
