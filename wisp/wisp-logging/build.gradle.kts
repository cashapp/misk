plugins {
    `java-library`
}

dependencies {
    implementation(libs.kotlinStdLibJdk8)
    implementation(libs.kotlinReflection)
    api(libs.loggingApi)
    implementation(libs.slf4jApi)

    testImplementation(libs.logbackClassic)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxCoroutines)
    testImplementation(project(":wisp-logging-testing"))
}
