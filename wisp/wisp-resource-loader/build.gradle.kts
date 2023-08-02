plugins {
    `java-library`
}

dependencies {
    implementation(libs.loggingApi)
    implementation(libs.okio)
    implementation(project(":wisp:wisp-logging"))
    runtimeOnly(libs.bouncycastle)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
}
