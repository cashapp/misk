plugins {
    `java-library`
}

dependencies {
    api(libs.kotlinRetry)
    api(libs.micrometerCore)
    api(project(":wisp:wisp-config"))
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinLogging)
    implementation(project(":wisp:wisp-logging"))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.micrometerPrometheus)
    testImplementation(libs.prometheusClient)
}
