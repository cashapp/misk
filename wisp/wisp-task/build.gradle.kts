plugins {
    `java-library`
}

dependencies {
    api(Dependencies.kotlinRetry)
    api(Dependencies.micrometerCore)
    api(project(":wisp:wisp-config"))
    implementation(Dependencies.kotlinxCoroutines)
    implementation(Dependencies.kotlinLogging)
    implementation(project(":wisp:wisp-logging"))

    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(Dependencies.micrometerPrometheus)
    testImplementation(Dependencies.prometheusClient)
}
