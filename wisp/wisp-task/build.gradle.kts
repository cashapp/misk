plugins {
    `java-library`
}

dependencies {
    api(libs.kotlinRetry)
    api(libs.micrometerCore)
    api(project(":wisp:wisp-config"))
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinLogging)
    implementation(project(":wisp:wisp-logging"))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.micrometerRegistryPrometheus)
    testImplementation(libs.prometheusClient)
}
