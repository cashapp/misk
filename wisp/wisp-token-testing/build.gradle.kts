plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}


dependencies {
    api(project(":wisp:wisp-token"))
    api(project(":misk-testing-api"))

    testImplementation(libs.kotestAssertionsShared)
    testImplementation(libs.kotestCommon)
    testImplementation(libs.kotestFrameworkApi)
    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.kotestJunitRunnerJvm)
}
