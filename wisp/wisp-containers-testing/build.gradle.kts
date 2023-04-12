plugins {
    `java-library`
}

dependencies {
    api(libs.dockerJavaApi)
    api(libs.loggingApi)
    implementation(libs.dockerCore)
    implementation(libs.dockerJavaTransport)
    implementation(libs.dockerTransport)
    implementation(project(":wisp-logging"))
    runtimeOnly(libs.logbackClassic)

    testImplementation(libs.junitApi)
}
