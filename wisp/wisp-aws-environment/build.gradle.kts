plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-deployment"))
    implementation(platform(libs.aws2Bom))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))
}
