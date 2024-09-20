plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(libs.kotlinRetry)
    api(libs.micrometerCore)
    api(project(":wisp:wisp-config"))
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.loggingApi)
    implementation(project(":wisp:wisp-logging"))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.micrometerRegistryPrometheus)
    testImplementation(libs.prometheusClient)
}
