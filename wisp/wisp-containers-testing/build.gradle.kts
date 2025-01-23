plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(libs.dockerApi)
    api(libs.loggingApi)
    implementation(libs.dockerCore)
    implementation(libs.dockerTransportHttpClient)
    implementation(libs.dockerTransportCore)
    implementation(project(":wisp:wisp-logging"))
    implementation(libs.openTracing)
    implementation(libs.openTracingUtil)
    runtimeOnly(libs.logbackClassic)

    testImplementation(libs.junitApi)
}
