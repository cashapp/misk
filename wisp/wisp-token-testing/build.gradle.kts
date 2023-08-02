plugins {
    `java-library`
}


dependencies {
    api(project(":wisp-token"))

    testImplementation(libs.kotestAssertionsShared)
    testImplementation(libs.kotestCommon)
    testImplementation(libs.kotestFrameworkApi)
    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.kotestJunitRunnerJvm)
}
