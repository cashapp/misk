plugins {
    `java-library`
}

dependencies {
    api(libs.loggingApi)
    implementation(libs.slf4jApi)
    implementation(project(":wisp-sampling"))

    testImplementation(libs.logbackClassic)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxCoroutines)
    testImplementation(project(":wisp-logging-testing"))
}
