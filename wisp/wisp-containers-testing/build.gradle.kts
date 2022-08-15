plugins {
    `java-library`
}

dependencies {
    implementation(libs.dockerCore)
    implementation(libs.dockerTransport)
    api(libs.loggingApi)
    api(libs.logbackClassic)
    api(project(":wisp-logging"))
}
