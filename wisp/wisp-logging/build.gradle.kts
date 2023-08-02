plugins {
    `java-library`
}

dependencies {
    api(Dependencies.kotlinLogging)
    api(Dependencies.slf4jApi)
    api(project(":wisp:wisp-sampling"))

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.logbackClassic)
    testImplementation(project(":wisp:wisp-logging-testing"))
}
