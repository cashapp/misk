plugins {
    `java-library`
}

dependencies {
    api(libs.kotlinRetry)
    api(libs.micrometerCore)
    api(project(":wisp-config"))
    implementation(libs.kotlinxCoroutines)
    implementation(libs.loggingApi)
    implementation(project(":wisp-logging"))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.micrometerPrometheus)
    testImplementation(libs.prometheusClient)
}
