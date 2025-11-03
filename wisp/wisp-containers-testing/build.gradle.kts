plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(libs.dockerApi)
    api(libs.loggingApi)
    implementation(libs.dockerCore)
    implementation(libs.dockerTransportHttpClient)
    implementation(libs.dockerTransportCore)
    implementation(project(":misk-docker"))
    implementation(project(":wisp:wisp-logging"))
    implementation(libs.openTracing)
    implementation(libs.openTracingUtil)
    runtimeOnly(libs.logbackClassic)

    testImplementation(libs.junitApi)
}
