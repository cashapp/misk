plugins {
    `java-library`
}

dependencies {
    api(Dependencies.launchDarkly)
    api(Dependencies.kotlinLogging)
    api(Dependencies.micrometerCore)
    api(Dependencies.moshi)
    api(Dependencies.openTracingApi)
    api(project(":wisp:wisp-client"))
    api(project(":wisp:wisp-config"))
    api(project(":wisp:wisp-feature"))
    api(project(":wisp:wisp-resource-loader"))
    api(project(":wisp:wisp-ssl"))
    implementation(Dependencies.openTracingDatadog)

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.mockitoCore)
    testImplementation(project(":wisp:wisp-moshi"))
    testImplementation(project(":wisp:wisp-tracing"))
}
