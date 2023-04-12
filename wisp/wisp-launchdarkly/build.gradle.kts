plugins {
    `java-library`
}

dependencies {
    api(libs.launchDarkly)
    api(libs.loggingApi)
    api(libs.moshiCore)
    api(project(":wisp-client"))
    api(project(":wisp-config"))
    api(project(":wisp-feature"))
    api(project(":wisp-resource-loader"))
    api(project(":wisp-ssl"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.mockitoCore)
    testImplementation(project(":wisp-moshi"))
}
