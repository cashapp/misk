plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}


dependencies {
    api(project(":wisp:wisp-token"))
    api(project(":misk-testing-api"))

    api(libs.jakartaInject)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestAssertionsShared)
    testImplementation(libs.kotestCommon)
    testImplementation(libs.kotestFrameworkEngine)
    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.kotestJunitRunnerJvm)
}
