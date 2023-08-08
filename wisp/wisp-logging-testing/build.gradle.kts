plugins {
    `java-library`
}

dependencies {
    api(Dependencies.logbackClassic)
    api(Dependencies.kotlinLogging)
    api(Dependencies.assertj)
    implementation(Dependencies.logbackCore)
    implementation(Dependencies.slf4jApi)

    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(project(":wisp:wisp-logging"))
}
