plugins {
    `java-library`
}

dependencies {
    api(libs.loggingApi)
    api(libs.logbackClassic)
    implementation(libs.slf4jApi)
    implementation(libs.assertj)

    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxCoroutines)
    testImplementation(project(":wisp-logging"))
}
