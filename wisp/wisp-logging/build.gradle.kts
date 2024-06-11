plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(libs.kotlinLogging)
    api(libs.slf4jApi)
    api(project(":wisp:wisp-sampling"))
  
    implementation(project(":misk-api"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.logbackClassic)
    testImplementation(project(":wisp:wisp-logging-testing"))
}
