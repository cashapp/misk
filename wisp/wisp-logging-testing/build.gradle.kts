plugins {
    `java-library`
}

dependencies {
    api(libs.logbackClassic)
    api(libs.loggingApi)
    api(libs.assertj)
    implementation(libs.logbackCore)
    implementation(libs.slf4jApi)

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp-logging"))
}
