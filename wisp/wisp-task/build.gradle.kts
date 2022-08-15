plugins {
    `java-library`
}

dependencies {
    implementation(libs.kotlinxCoroutines)
    api(libs.kotlinRetry)
    api(libs.micrometerPrometheus)
    implementation(project(":wisp-config"))
    implementation(project(":wisp-logging"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
}
