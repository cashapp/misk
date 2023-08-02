plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-deployment"))
    implementation(platform(libs.aws2Bom))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp:wisp-deployment-testing"))
}
