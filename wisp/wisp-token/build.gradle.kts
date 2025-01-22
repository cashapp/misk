plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestAssertionsShared)
    testImplementation(libs.kotestCommon)
    testImplementation(libs.kotestFrameworkApi)
    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.kotestJunitRunnerJvm)
}
