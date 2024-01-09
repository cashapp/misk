plugins {
    `java-library`
}

dependencies {
    api(libs.dockerApi)
    api(libs.kotlinLogging)
    implementation(libs.dockerCore)
    implementation(libs.dockerTransport)
    implementation(libs.dockerTransportHttpClient)
    implementation(project(":wisp:wisp-logging"))
    runtimeOnly(libs.logbackClassic)

    testImplementation(libs.junitApi)
}
