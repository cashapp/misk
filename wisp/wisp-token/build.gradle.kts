plugins {
    `java-library`
}

dependencies {
    testImplementation(Dependencies.kotestAssertions)
    testImplementation(Dependencies.kotestAssertionsShared)
    testImplementation(Dependencies.kotestCommon)
    testImplementation(Dependencies.kotestFrameworkApi)
    testRuntimeOnly(Dependencies.junitEngine)
    testRuntimeOnly(Dependencies.kotestJunitRunnerJvm)
}
