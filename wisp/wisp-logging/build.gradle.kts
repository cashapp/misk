plugins {
    `java-library`
}

dependencies {
    api(libs.loggingApi)
    api(libs.slf4jApi)
    api(project(":wisp-sampling"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.logbackClassic)
    testImplementation(project(":wisp-logging-testing"))
}
