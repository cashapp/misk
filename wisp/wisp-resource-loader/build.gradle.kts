plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    implementation(libs.kotlinLogging)
    implementation(libs.okio)
    implementation(project(":wisp:wisp-logging"))
    runtimeOnly(libs.bouncycastle)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
}
