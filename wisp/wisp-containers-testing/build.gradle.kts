plugins {
    `java-library`
}

dependencies {
    api(Dependencies.dockerApi)
    api(Dependencies.kotlinLogging)
    implementation(Dependencies.dockerCore)
    implementation(Dependencies.dockerTransport)
    implementation(Dependencies.dockerTransportHttpClient)
    implementation(project(":wisp:wisp-logging"))
    runtimeOnly(Dependencies.logbackClassic)

    testImplementation(Dependencies.junitApi)
}
